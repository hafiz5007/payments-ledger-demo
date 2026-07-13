package com.hafiz5007.ledger.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hafiz5007.ledger.application.PostPaymentResult;
import com.hafiz5007.ledger.infrastructure.services.PostPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inbound {@code payments.submitted} consumer.
 * <p>
 * The DB transaction opened by {@link PostPaymentService#execute} covers
 * the entry write + posting writes + balance projection updates + outbox
 * insert. The Kafka offset commit only happens after the transaction
 * commits, so effectively-once processing holds:
 * <ul>
 *   <li>DB commit success + offset commit success → normal path</li>
 *   <li>DB commit success + offset commit failure → the message is redelivered;
 *       the idempotency key short-circuits the second run.</li>
 *   <li>DB commit failure → rollback, offset not committed, retry via the
 *       {@link LedgerKafkaConfig#kafkaErrorHandler} until finally publishing
 *       to {@link LedgerTopics#PAYMENTS_SUBMITTED_DLT}.</li>
 * </ul>
 */
@Component
public class PaymentSubmittedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentSubmittedConsumer.class);

    private final PostPaymentService service;
    private final PaymentSubmittedMapper mapper;
    private final ObjectMapper json;

    public PaymentSubmittedConsumer(PostPaymentService service,
                                    PaymentSubmittedMapper mapper,
                                    ObjectMapper json) {
        this.service = service;
        this.mapper = mapper;
        this.json = json;
    }

    @KafkaListener(topics = LedgerTopics.PAYMENTS_SUBMITTED, groupId = "${spring.kafka.consumer.group-id:ledger}")
    public void onMessage(String rawJson) {
        PaymentSubmittedMessage message;
        try {
            message = json.readValue(rawJson, PaymentSubmittedMessage.class);
        } catch (Exception ex) {
            // Malformed JSON — not retryable. Throw so the error handler routes
            // straight to the DLT after the exponential-backoff retries.
            throw new IllegalArgumentException("Cannot parse PaymentSubmitted JSON: " + ex.getMessage(), ex);
        }

        var instruction = mapper.toDomain(message);
        var result = service.execute(instruction);

        // We log the outcome but do not throw on business rejections — they
        // are valid domain outcomes, not "consumer failures". The publisher
        // upstream reads the PaymentFailed event from the outbox topic.
        switch (result) {
            case PostPaymentResult.PostedNew p ->
                log.info("Posted payment tx={} entry={} amount={}",
                    message.transactionId(), p.ledgerEntryId(), p.amount());
            case PostPaymentResult.AlreadyPosted p ->
                log.info("Duplicate payment tx={} entry={} — no-op",
                    message.transactionId(), p.ledgerEntryId());
            case PostPaymentResult.Rejected r ->
                log.info("Rejected payment tx={} reason={} — {}",
                    message.transactionId(), r.reason(), r.detail());
        }
    }
}
