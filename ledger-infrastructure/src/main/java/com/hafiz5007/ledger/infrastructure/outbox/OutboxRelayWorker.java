package com.hafiz5007.ledger.infrastructure.outbox;

import com.hafiz5007.ledger.domain.ports.Clock;
import com.hafiz5007.ledger.infrastructure.entities.OutboxEntity;
import com.hafiz5007.ledger.infrastructure.kafka.LedgerTopics;
import com.hafiz5007.ledger.infrastructure.repositories.OutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The outbox relay. On a fixed cadence: read a batch of unsent outbox rows,
 * publish each to the matching Kafka topic, mark sent. Runs on a single node
 * — in a multi-replica deployment you'd fence with a Postgres advisory lock
 * so only one replica sweeps at a time.
 * <p>
 * Ordering: rows are read in {@code created_at ASC} order. On any publish
 * failure we stop the batch immediately — the next tick retries starting
 * from the same failed row, preserving per-aggregate order. That's stricter
 * than needed for at-least-once delivery but it keeps the story simple for
 * a demo.
 * <p>
 * Delivery guarantee: at-least-once. Consumers of the outbound topics must
 * be idempotent on the event id (which is stable across resends — the
 * outbox row's primary key).
 */
@Component
public class OutboxRelayWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayWorker.class);

    private final OutboxJpaRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxRelayProperties properties;
    private final Clock clock;

    public OutboxRelayWorker(OutboxJpaRepository outbox,
                             KafkaTemplate<String, String> kafka,
                             OutboxRelayProperties properties,
                             Clock clock) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "#{@outboxRelayProperties.pollInterval.toMillis()}")
    @Transactional
    public void sweep() {
        List<OutboxEntity> batch = outbox.findUnsent(PageRequest.of(0, properties.batchSize()));
        if (batch.isEmpty()) return;

        int published = 0;
        for (OutboxEntity row : batch) {
            try {
                publish(row);
                row.markSent(clock.nowUtc());
                outbox.save(row);
                published++;
            } catch (Exception ex) {
                // Break the batch: the same row will be first on the next tick.
                // In a multi-tenant / high-throughput system you'd continue with
                // rows for OTHER aggregates and only halt on the failed aggregate;
                // this demo keeps it simple.
                log.warn("Outbox publish failed for row {} ({}) — will retry next tick: {}",
                    row.id(), row.eventType(), ex.getMessage());
                break;
            }
        }
        if (published > 0) log.info("Outbox relay published {} events", published);
    }

    private void publish(OutboxEntity row) throws InterruptedException, ExecutionException, TimeoutException {
        String topic = topicFor(row.eventType());
        // Send synchronously so a failure surfaces here — the sweep loop's
        // catch block handles it.
        kafka.send(topic, row.aggregateId(), row.payload())
             .get(5, TimeUnit.SECONDS);
    }

    private static String topicFor(String eventType) {
        return switch (eventType) {
            case "PaymentPosted"   -> LedgerTopics.PAYMENTS_POSTED;
            case "PaymentFailed"   -> LedgerTopics.PAYMENTS_FAILED;
            case "AccountCreated"  -> LedgerTopics.ACCOUNTS_CREATED;
            default -> throw new IllegalStateException("No topic mapping for event type: " + eventType);
        };
    }
}
