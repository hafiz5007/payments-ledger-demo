package com.hafiz5007.ledger.infrastructure.adapters;

import com.hafiz5007.ledger.domain.event.DomainEvent;
import com.hafiz5007.ledger.domain.ports.Clock;
import com.hafiz5007.ledger.domain.ports.OutboxPublisher;
import com.hafiz5007.ledger.infrastructure.entities.OutboxEntity;
import com.hafiz5007.ledger.infrastructure.repositories.OutboxJpaRepository;
import com.hafiz5007.ledger.infrastructure.serialization.DomainEventJsonMapper;

import java.util.UUID;

/**
 * The outbox side of the outbox pattern. This adapter INSERTs into the
 * {@code outbox} table only — the relay worker in Phase 4 reads unsent
 * rows, publishes to Kafka, and stamps {@code sent_at}.
 * <p>
 * Because this insert shares a JPA transaction with the ledger entry write
 * (both are called inside the same {@code @Transactional} use case), either
 * both persist or neither does. There is no "wrote to Kafka but crashed
 * before persisting the ledger" failure mode.
 */
public final class JpaOutboxPublisher implements OutboxPublisher {

    private final OutboxJpaRepository outbox;
    private final DomainEventJsonMapper json;
    private final Clock clock;

    public JpaOutboxPublisher(OutboxJpaRepository outbox, DomainEventJsonMapper json, Clock clock) {
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void publish(DomainEvent event) {
        var serialized = json.serialize(event);
        outbox.save(new OutboxEntity(
            UUID.randomUUID(),
            serialized.eventType(),
            serialized.aggregateId(),
            serialized.payload(),
            clock.nowUtc()
        ));
    }
}
