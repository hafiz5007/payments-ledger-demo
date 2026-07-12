package com.hafiz5007.ledger.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox row awaiting Kafka publication. Written inside the same DB
 * transaction as the ledger entry it accompanies — no dual-write
 * inconsistency possible.
 * <p>
 * Payload is JSONB; Hibernate 6 maps {@code String} + {@code @JdbcTypeCode(JSON)}
 * to Postgres native JSONB so queries can filter on payload fields if we
 * ever need to (e.g. "resend everything about payment X").
 */
@Entity
@Table(name = "outbox")
public class OutboxEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxEntity() { }

    public OutboxEntity(UUID id, String eventType, String aggregateId, String payload, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public void markSent(Instant at) { this.sentAt = at; }

    public UUID id() { return id; }
    public String eventType() { return eventType; }
    public String aggregateId() { return aggregateId; }
    public String payload() { return payload; }
    public Instant createdAt() { return createdAt; }
    public Instant sentAt() { return sentAt; }
}
