package com.hafiz5007.ledger.infrastructure.kafka;

/**
 * Wire format for the inbound {@code payments.submitted} topic.
 * <p>
 * A wire DTO lives in infrastructure, deliberately separate from the
 * Domain's {@link com.hafiz5007.ledger.domain.model.PaymentInstruction}.
 * If the upstream producer's JSON shape changes (adds a field, renames one)
 * this file changes and the domain doesn't. Same evolution rule as JPA
 * entities — the shape at the boundary is not the shape in the middle.
 */
public record PaymentSubmittedMessage(
    String paymentId,
    String transactionId,       // idempotency key
    String fromAccountId,
    String toAccountId,
    String amount,              // decimal string, e.g. "42.50"
    String currency,            // ISO 4217, e.g. "GBP"
    String reference
) { }
