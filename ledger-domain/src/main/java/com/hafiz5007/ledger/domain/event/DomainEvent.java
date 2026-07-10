package com.hafiz5007.ledger.domain.event;

import java.time.Instant;

/**
 * Marker for anything raised from the domain. Sealed so the compiler
 * enforces the closed set of event types — a future infra module can
 * exhaustively handle every case with a switch expression.
 */
public sealed interface DomainEvent
    permits AccountCreatedEvent,
            PaymentPostedEvent,
            PaymentFailedEvent {

    Instant occurredAtUtc();
}
