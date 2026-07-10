package com.hafiz5007.ledger.domain.event;

import com.hafiz5007.ledger.domain.model.PaymentId;
import com.hafiz5007.ledger.domain.model.TransactionId;

import java.time.Instant;

public record PaymentFailedEvent(
    PaymentId paymentId,
    TransactionId transactionId,
    FailureReason reason,
    String detail,
    Instant occurredAtUtc
) implements DomainEvent {

    public enum FailureReason {
        ACCOUNT_NOT_FOUND,
        ACCOUNT_INACTIVE,
        CURRENCY_MISMATCH,
        INSUFFICIENT_FUNDS,
        DUPLICATE_TRANSACTION,          // idempotency short-circuit — not really a failure
        VALIDATION_FAILED
    }
}
