package com.hafiz5007.ledger.infrastructure.adapters;

import com.hafiz5007.ledger.domain.ports.Clock;

import java.time.Instant;

public final class SystemClock implements Clock {
    @Override public Instant nowUtc() { return Instant.now(); }
}
