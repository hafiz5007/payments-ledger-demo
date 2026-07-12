# payments-ledger-demo

A double-entry accounting ledger with Kafka-based payment ingestion, event sourcing, and the outbox pattern for effectively-once downstream delivery. Built in Java 21 + Spring Boot 3 phase-by-phase, with Clean Architecture enforced by Gradle module boundaries.

> A senior backend interviewer will ask you three things about a payments system: how you keep the books balanced, how you handle idempotency, and how you deliver events without double-publishing. This repo answers all three.

**Status: Phase 3 of 5 — Infrastructure (Postgres + JPA + outbox).**

## Phases

| Phase | Ships | Status |
| --- | --- | --- |
| **1 — Domain** | Java 21 records: `Money` (BigDecimal + currency safety), `Posting`, `LedgerEntry` (balancing invariant in the constructor). Service interfaces (ports). Domain events as a sealed interface hierarchy. Unit tests. Zero framework deps. | **done** |
| **2 — Application** | `PostPaymentUseCase` with idempotency + sufficient-funds check, `CreateAccountUseCase`, `GetAccountBalanceUseCase`. Sealed `PostPaymentResult` for the three outcomes. Full test suite over in-memory fakes. | **done** |
| **3 — Infrastructure** | Postgres 16 + JPA + Flyway migration. JPA entities + Spring Data repositories + adapters implementing every Domain port. Balance projection table updated in the same transaction as postings. Outbox table with JSONB payloads + partial index on unsent rows. Jackson-based event serialiser. Single `@Configuration` wires it all up. | **done** |
| **4 — Kafka** | Consumer for inbound `PaymentSubmitted`, outbox-relay worker publishing to `PaymentPosted`. | pending |
| **5 — Boot host + REST + Docker + CI** | Runnable Spring Boot app, docker-compose (Postgres + Kafka + app), CI, architecture diagrams. | pending |

Read the git history — each phase is a single commit that stands on its own.

## What Phase 1 gives you

A `ledger-domain` Gradle sub-module with **zero** Spring, JPA, Jackson, or Kafka dependencies. If you type `import org.springframework...` inside this module, the build fails at compile time — those packages aren't on the classpath.

The interesting types:

- **`Money`** — `BigDecimal` + `Currency` value object. Currency-safe arithmetic (adding GBP + USD throws). Scale normalised to the currency's fraction digits so `10` and `10.00` GBP compare equal. Constructor rejects extra precision (`10.001` GBP is illegal). No floating point. Anywhere.
- **`Posting`** — one side of a double-entry, with `AccountId`, `PostingSide` (DEBIT / CREDIT), and a positive `Money` amount. Rejects zero and negative amounts.
- **`LedgerEntry`** — a record that enforces the balancing invariant *in its constructor*. You cannot construct an entry where debits don't equal credits per currency. Every downstream consumer gets to assume the invariant.
- **Sealed `DomainEvent`** hierarchy — `AccountCreatedEvent`, `PaymentPostedEvent`, `PaymentFailedEvent`. Exhaustive `switch` becomes safe.
- **Ports** — `AccountRepository`, `LedgerEntryStore`, `IdempotencyStore`, `OutboxPublisher`, `Clock`. Every side effect from the domain is behind an interface.
- **Services** — `DoubleEntryValidator` for rules beyond balancing, `LedgerPoster` that turns a `PaymentInstruction` into a balanced entry.

Fifteen JUnit 5 tests covering `Money` arithmetic + safety, the `LedgerEntry` balancing invariant (including a multi-currency case), and `LedgerPoster` mapping.

## What Phase 2 gives you

`ledger-application` — a second Gradle sub-module that depends on `ledger-domain` and nothing else. No Spring, no JPA, no Kafka. The compiler enforces it.

- **`PostPaymentUseCase`** — the write path for a payment. Runs the full pipeline: idempotency short-circuit → account lookups → active checks → currency alignment → sufficient-funds check → post + record idempotency + publish `PaymentPostedEvent`. Rejections short-circuit with a `PostPaymentResult.Rejected` and publish a `PaymentFailedEvent` for downstream systems.
- **`PostPaymentResult`** — sealed interface with three cases: `PostedNew`, `AlreadyPosted`, `Rejected`. Callers pattern-match; new cases would force every switch to handle them.
- **`CreateAccountUseCase`** — provisions accounts, publishes `AccountCreatedEvent`.
- **`GetAccountBalanceUseCase`** — read-side query that delegates to the store port so a Phase 3 projection swap doesn't touch this class.

**Tests** — 12 across three test classes running against in-memory fakes (`InMemoryAccountRepository`, `InMemoryLedgerEntryStore`, `InMemoryIdempotencyStore`, `CapturingOutboxPublisher`, `FixedClock`). A shared `LedgerTestFixture` wires the DI graph in one line per test. The Postgres implementation in Phase 3 will not require any of these tests to change — the ports are the seam.

Key scenarios covered:
- Happy path (write, idempotency, event, balance update)
- **Duplicate submission returns `AlreadyPosted` with the same entry id, no double-write, no double-event** — this is the effectively-once story working end-to-end
- Missing / inactive account (both from and to)
- Currency mismatch
- Insufficient funds (with a boundary test for exact-balance-allowed)
- Multi-payment balance accumulation

## What Phase 3 gives you

`ledger-infrastructure` — the Spring Data JPA + Postgres implementation of every Domain port. This module DOES depend on Spring; that's what infrastructure is for.

### Schema (Flyway `V1__initial_schema.sql`)

Six tables. Key design points:

- **`ledger_entries` + `postings`** are separate tables joined by `ledger_entry_id`. Postings sit on the many side because a single entry can hit 2, 3, or N accounts (the domain enforces balancing per currency; SQL doesn't try to).
- **`account_balances`** is a projection table, not a materialized view — one row per account, `amount` + `currency`, updated in the same JPA transaction as posting inserts. Reads are O(1). A nightly reconciliation job (roadmap) checks the projection against `SUM(postings.signed_amount)` and alerts on drift.
- **`idempotency_keys`** — `TransactionId → LedgerEntryId`. The primary key catches concurrent submitters at the DB level: two POST requests for the same idempotency key can't both create ledger entries because the second `INSERT` fails.
- **`outbox`** — pending events awaiting Kafka. JSONB payload (so we can query on payload fields when we need to). **Partial index on `sent_at IS NULL`** so the relay's "pending" scan stays cheap as the sent table grows to millions of rows.

### Adapters

Each Domain port has a JPA-backed implementation:

- **`JpaAccountRepository`** — CRUD via `AccountJpaRepository`. On new-account save, bootstraps a zero-balance row in `account_balances` so subsequent postings can assume it exists.
- **`JpaLedgerEntryStore`** — the substantive one. `append(entry)` inserts the entry header, N posting rows, and updates N balance projections in a single transaction. `findByIdForUpdate` on the balance row (SELECT ... FOR UPDATE) serialises concurrent posters on the same account.
- **`JpaIdempotencyStore`** — insert on `record`, lookup on `findExistingEntry`. Primary key uniqueness enforces the invariant.
- **`JpaOutboxPublisher`** — serialises the `DomainEvent` to JSON via `DomainEventJsonMapper` and inserts one outbox row. Runs inside the same transaction as the ledger write, so a mid-flight crash rolls both back.

### Event serialisation

`DomainEventJsonMapper` uses Jackson to turn a `DomainEvent` into `(event_type, aggregate_id, JSON payload)`. Domain has zero Jackson dependency; serialisation lives at this layer. The `aggregate_id` is what Kafka partitions on: all events for one payment / one account go to the same partition, preserving downstream ordering.

### Wiring

One `@Configuration` class (`InfrastructureConfig`) exposes every port as a Spring bean. Phase 5's Boot host imports it with a single annotation. No component scanning across module boundaries — every bean is defined explicitly.

## Build (Phase 1)

```bash
./gradlew :ledger-domain:build
./gradlew :ledger-domain:test
```

The `:ledger-domain:test` task runs the whole suite in under a second because there's no Spring context to bootstrap.

## Why Java 21 (not Kotlin)?

Kotlin covers KYC in the portfolio; Java 21 covers this one. Records + pattern matching + sealed types give Java the same "make illegal states unrepresentable" ergonomics Kotlin has with data classes and sealed classes. Any senior JVM interviewer wants to see both.

Virtual threads land in Phase 5's Spring Boot host — the `PaymentSubmitted` consumer runs one virtual thread per message with structured concurrency for the DB write + outbox write.

## Architecture roadmap

By Phase 5:

- **Ports and adapters** — the domain talks to storage and messaging through interfaces (`LedgerEntryStore`, `OutboxPublisher`) implemented in the infrastructure module. Swap Postgres for CockroachDB or Kafka for Pulsar without touching the domain.
- **Event sourcing** on the ledger side — every state change is a persisted `LedgerEntry`. Account balances are projections. Auditability is free; time-travel is free.
- **Outbox pattern** for downstream delivery — the ledger write and the outbox write share one DB transaction. A separate relay worker (Phase 4) reads outbox rows and publishes to Kafka, then marks them sent. No dual-write inconsistency; at-least-once to Kafka; consumers are idempotent.
- **Effectively-once** payment handling — every inbound instruction carries a client-supplied `TransactionId`. Duplicate submissions return the previous result rather than re-posting. True exactly-once delivery is impossible; effectively-once processing is achievable.

## License

MIT — see [LICENSE](LICENSE).
