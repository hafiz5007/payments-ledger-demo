-- Initial schema for payments-ledger-demo.
--
-- Six tables:
--   accounts             — the account catalogue (name, type, currency)
--   ledger_entries       — append-only header rows
--   postings             — one row per side of a double-entry
--   account_balances     — read projection; updated atomically with append
--   idempotency_keys     — TransactionId -> LedgerEntryId dedup
--   outbox               — pending DomainEvents awaiting Kafka publication
--
-- All monetary amounts are stored as (numeric(19,4), varchar(3)) pairs.
-- 19 total / 4 fractional gives us up to £999,999,999,999,999.9999 which
-- is fine even for the world's largest single payment.

CREATE TABLE accounts (
    id             UUID         PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    type           VARCHAR(16)  NOT NULL CHECK (type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    currency       VARCHAR(3)   NOT NULL,
    created_at_utc TIMESTAMPTZ  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_accounts_type ON accounts (type);

-- ─────────────────────────────────────────────────────────────────
-- Ledger entries and postings.
--
-- Entries are the atomic accounting event. Postings are their line items;
-- each entry has 2+ postings that balance to zero per currency (enforced
-- in domain code, not in SQL — a CHECK constraint here would be too
-- expensive on write).
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE ledger_entries (
    id              UUID         PRIMARY KEY,
    transaction_id  VARCHAR(64)  NOT NULL,
    occurred_at_utc TIMESTAMPTZ  NOT NULL,
    description     VARCHAR(500) NOT NULL
);
CREATE INDEX ix_ledger_entries_occurred_at ON ledger_entries (occurred_at_utc);

CREATE TABLE postings (
    id              BIGSERIAL      PRIMARY KEY,
    ledger_entry_id UUID           NOT NULL REFERENCES ledger_entries(id) ON DELETE RESTRICT,
    account_id      UUID           NOT NULL REFERENCES accounts(id),
    side            VARCHAR(6)     NOT NULL CHECK (side IN ('DEBIT','CREDIT')),
    amount          NUMERIC(19,4)  NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3)     NOT NULL
);
CREATE INDEX ix_postings_account_id ON postings (account_id);
CREATE INDEX ix_postings_ledger_entry ON postings (ledger_entry_id);

-- ─────────────────────────────────────────────────────────────────
-- Balance projection. Updated in the same transaction as posting inserts;
-- balanceOf() reads this table in O(1) rather than summing postings.
-- The (account_id) unique key + upsert makes concurrent writers safe.
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE account_balances (
    account_id  UUID          PRIMARY KEY REFERENCES accounts(id),
    amount      NUMERIC(19,4) NOT NULL,
    currency    VARCHAR(3)    NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────────
-- Idempotency: TransactionId -> LedgerEntryId. Unique key catches
-- concurrent submitters at the DB level so we never write two entries
-- for the same transaction id.
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE idempotency_keys (
    transaction_id  VARCHAR(64) PRIMARY KEY,
    ledger_entry_id UUID        NOT NULL REFERENCES ledger_entries(id),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────────
-- Outbox: DomainEvents queued for downstream Kafka publication.
-- Inserted in the same DB transaction as the ledger entry write.
-- Phase 4's relay worker reads unsent rows, publishes to Kafka, and
-- stamps sent_at.
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE outbox (
    id          UUID         PRIMARY KEY,
    event_type  VARCHAR(64)  NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,      -- for Kafka partitioning by aggregate
    payload     JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at     TIMESTAMPTZ                 -- NULL until the relay publishes
);
-- Partial index so the relay's "SELECT ... WHERE sent_at IS NULL" is cheap
-- as the table grows to millions of rows; sent rows stay indexed on a small
-- pending-only sub-tree.
CREATE INDEX ix_outbox_unsent ON outbox (created_at) WHERE sent_at IS NULL;
