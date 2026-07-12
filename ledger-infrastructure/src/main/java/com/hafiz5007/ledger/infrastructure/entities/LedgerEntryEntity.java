package com.hafiz5007.ledger.infrastructure.entities;

import com.hafiz5007.ledger.domain.model.LedgerEntry;
import com.hafiz5007.ledger.domain.model.LedgerEntryId;
import com.hafiz5007.ledger.domain.model.TransactionId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The header row for a single ledger entry. Its postings live in a separate
 * table ({@link PostingEntity}) linked by {@code ledger_entry_id}. Kept as
 * separate tables so a single balanced posting can hit multiple accounts and
 * per-account queries are efficient.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntryEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    @Column(name = "occurred_at_utc", nullable = false)
    private Instant occurredAtUtc;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    protected LedgerEntryEntity() { }

    public static LedgerEntryEntity headerOf(LedgerEntry entry) {
        var e = new LedgerEntryEntity();
        e.id = entry.id().value();
        e.transactionId = entry.transactionId().value();
        e.occurredAtUtc = entry.occurredAtUtc();
        e.description = entry.description();
        return e;
    }

    public UUID id() { return id; }
    public LedgerEntryId asDomainId() { return new LedgerEntryId(id); }
    public TransactionId asTransactionId() { return new TransactionId(transactionId); }
    public String transactionId() { return transactionId; }
    public Instant occurredAtUtc() { return occurredAtUtc; }
    public String description() { return description; }
}
