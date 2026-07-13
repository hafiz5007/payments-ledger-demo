package com.hafiz5007.ledger.infrastructure.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ledger.outbox")
public record OutboxRelayProperties(
    /** How often the relay wakes up and looks for pending rows. */
    Duration pollInterval,
    /** Max rows the relay processes per tick. */
    int batchSize
) {
    public OutboxRelayProperties {
        if (pollInterval == null) pollInterval = Duration.ofSeconds(1);
        if (batchSize <= 0) batchSize = 100;
    }
}
