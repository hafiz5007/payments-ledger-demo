package com.hafiz5007.ledger.infrastructure.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hafiz5007.ledger.domain.event.AccountCreatedEvent;
import com.hafiz5007.ledger.domain.event.DomainEvent;
import com.hafiz5007.ledger.domain.event.PaymentFailedEvent;
import com.hafiz5007.ledger.domain.event.PaymentPostedEvent;

import java.util.Objects;

/**
 * Turns a {@link DomainEvent} into (event_type, aggregate_id, JSON payload)
 * for the outbox row, and back again when the relay worker reads it.
 * <p>
 * The domain has zero JSON dependency — Jackson lives here so a JSON-format
 * change never triggers a domain rebuild.
 * <p>
 * The {@code aggregate_id} field is what Kafka partitions on: all events
 * for one payment / one account land on the same partition so downstream
 * ordering is preserved.
 */
public final class DomainEventJsonMapper {

    private final ObjectMapper mapper;

    public DomainEventJsonMapper() {
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Serialized serialize(DomainEvent event) {
        Objects.requireNonNull(event, "event required");
        try {
            var payload = mapper.writeValueAsString(event);
            return new Serialized(typeOf(event), aggregateIdOf(event), payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise " + event.getClass().getSimpleName(), e);
        }
    }

    public DomainEvent deserialize(String eventType, String payload) {
        Class<? extends DomainEvent> clazz = switch (eventType) {
            case "PaymentPosted"    -> PaymentPostedEvent.class;
            case "PaymentFailed"    -> PaymentFailedEvent.class;
            case "AccountCreated"   -> AccountCreatedEvent.class;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
        try {
            return mapper.readValue(payload, clazz);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialise " + eventType, e);
        }
    }

    /**
     * The short logical name written to the outbox {@code event_type} column.
     * Deliberately not the full class name — decouples the DB schema from a
     * package-name refactor.
     */
    public static String typeOf(DomainEvent event) {
        return switch (event) {
            case PaymentPostedEvent ignored  -> "PaymentPosted";
            case PaymentFailedEvent ignored  -> "PaymentFailed";
            case AccountCreatedEvent ignored -> "AccountCreated";
        };
    }

    /** Partition key for Kafka. Aggregate = the root entity the event is about. */
    public static String aggregateIdOf(DomainEvent event) {
        return switch (event) {
            case PaymentPostedEvent e    -> e.paymentId().toString();
            case PaymentFailedEvent e    -> e.paymentId().toString();
            case AccountCreatedEvent e   -> e.accountId().toString();
        };
    }

    public record Serialized(String eventType, String aggregateId, String payload) { }
}
