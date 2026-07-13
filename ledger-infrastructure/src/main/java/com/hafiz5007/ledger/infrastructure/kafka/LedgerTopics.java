package com.hafiz5007.ledger.infrastructure.kafka;

/**
 * Topic name constants. Kept in one place so a rename doesn't require a
 * hunt through consumer + producer + config code.
 * <p>
 * Naming convention: {@code payments.<noun>.<past-participle>} for domain
 * events; {@code .retry} and {@code .dlt} suffixes for Spring Kafka's
 * default retry / dead-letter topic pattern.
 */
public final class LedgerTopics {

    private LedgerTopics() { }

    /** Inbound: payment instructions submitted upstream. */
    public static final String PAYMENTS_SUBMITTED     = "payments.submitted";

    /** Outbound: ledger entries the outbox relay publishes. */
    public static final String PAYMENTS_POSTED        = "payments.posted";

    /** Outbound: rejected payments the outbox relay publishes. */
    public static final String PAYMENTS_FAILED        = "payments.failed";

    /** Outbound: account provisioning events. */
    public static final String ACCOUNTS_CREATED       = "accounts.created";

    /** Auto-created by Spring Kafka's default error handler on retryable failure. */
    public static final String PAYMENTS_SUBMITTED_DLT = "payments.submitted.dlt";
}
