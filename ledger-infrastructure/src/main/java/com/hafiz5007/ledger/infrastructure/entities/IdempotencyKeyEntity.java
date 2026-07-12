package com.hafiz5007.ledger.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected IdempotencyKeyEntity() { }

    public IdempotencyKeyEntity(String transactionId, UUID ledgerEntryId, Instant recordedAt) {
        this.transactionId = transactionId;
        this.ledgerEntryId = ledgerEntryId;
        this.recordedAt = recordedAt;
    }

    public String transactionId() { return transactionId; }
    public UUID ledgerEntryId() { return ledgerEntryId; }
    public Instant recordedAt() { return recordedAt; }
}
