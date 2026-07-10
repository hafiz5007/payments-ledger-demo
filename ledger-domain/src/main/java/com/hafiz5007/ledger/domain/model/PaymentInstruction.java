package com.hafiz5007.ledger.domain.model;

import java.util.Objects;

/**
 * Inbound payment request — comes off the Kafka topic in Phase 4. Domain
 * doesn't care where it originated; the message shape lives in
 * infrastructure and is mapped to this before the ledger sees it.
 * <p>
 * Uses {@link TransactionId} as the idempotency key. Two instructions with
 * the same {@code transactionId} are treated as the same logical event —
 * the ledger returns the previous result rather than re-posting.
 */
public record PaymentInstruction(
    PaymentId paymentId,
    TransactionId idempotencyKey,
    AccountId fromAccountId,
    AccountId toAccountId,
    Money amount,
    String reference
) {
    public PaymentInstruction {
        Objects.requireNonNull(paymentId, "paymentId required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey required");
        Objects.requireNonNull(fromAccountId, "fromAccountId required");
        Objects.requireNonNull(toAccountId, "toAccountId required");
        Objects.requireNonNull(amount, "amount required");
        Objects.requireNonNull(reference, "reference required");

        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("From and to accounts must differ");
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Payment amount must be positive; got " + amount);
        }
    }
}
