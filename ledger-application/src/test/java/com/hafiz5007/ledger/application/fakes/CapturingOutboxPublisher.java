package com.hafiz5007.ledger.application.fakes;

import com.hafiz5007.ledger.domain.event.DomainEvent;
import com.hafiz5007.ledger.domain.ports.OutboxPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures every published event so tests can assert on the outbox stream.
 * A real Postgres-backed outbox would insert rows into a table, transactional
 * with the ledger entry write. Phase 3 shows that.
 */
public final class CapturingOutboxPublisher implements OutboxPublisher {

    private final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
        published.add(event);
    }

    public List<DomainEvent> published() { return List.copyOf(published); }

    public int size() { return published.size(); }

    /** Convenience: filter by event type. */
    public <T extends DomainEvent> List<T> ofType(Class<T> type) {
        return published.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .toList();
    }
}
