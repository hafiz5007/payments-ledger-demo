package com.hafiz5007.ledger.application.fakes;

import com.hafiz5007.ledger.domain.ports.Clock;

import java.time.Duration;
import java.time.Instant;

public final class FixedClock implements Clock {
    private Instant now;

    public FixedClock(Instant start) { this.now = start; }

    @Override public Instant nowUtc() { return now; }

    public void advance(Duration by) { now = now.plus(by); }

    public void set(Instant at) { now = at; }
}
