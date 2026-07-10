package com.hafiz5007.ledger.domain.ports;

import java.time.Instant;

/**
 * Injected clock. Every service that needs "now" takes a {@link Clock}
 * rather than calling {@link java.time.Instant#now()}. Makes tests
 * deterministic; lets integration tests time-travel to exercise expiry
 * without waiting.
 * <p>
 * Yes, {@link java.time.Clock} exists in the JDK — we roll our own because
 * that class has methods we don't need ({@code millis}, {@code getZone})
 * and forcing the whole domain to depend on the JDK Clock API is more
 * surface than we want.
 */
public interface Clock {
    Instant nowUtc();

    static Clock systemUtc() {
        return java.time.Clock.systemUTC()::instant;
    }
}
