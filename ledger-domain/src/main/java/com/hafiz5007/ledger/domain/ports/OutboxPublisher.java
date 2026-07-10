package com.hafiz5007.ledger.domain.ports;

import com.hafiz5007.ledger.domain.event.DomainEvent;

/**
 * The reliable-delivery seam. Writing to the outbox happens in the same
 * database transaction as the ledger entry write — if either fails, both
 * roll back. A separate relay worker (Phase 4) reads the outbox table and
 * publishes to Kafka, then marks the row as sent.
 * <p>
 * The pattern gives us: no lost events (the DB transaction guarantees the
 * outbox row is durably persisted before we ack the input), no
 * dual-write inconsistency (we never publish to Kafka and then fail to
 * persist), and no need for XA / two-phase commit.
 * <p>
 * The trade-off is at-least-once delivery to Kafka — the relay might publish
 * an event and crash before marking it sent, so consumers must be idempotent.
 * The idempotency key is the event's own id.
 */
public interface OutboxPublisher {

    /**
     * Queue an event for downstream publication. The write is transactional
     * with respect to the ledger entry write in the same use case call.
     */
    void publish(DomainEvent event);
}
