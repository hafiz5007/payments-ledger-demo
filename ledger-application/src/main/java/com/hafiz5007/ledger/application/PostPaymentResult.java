package com.hafiz5007.ledger.application;

import com.hafiz5007.ledger.domain.event.PaymentFailedEvent.FailureReason;
import com.hafiz5007.ledger.domain.model.LedgerEntryId;
import com.hafiz5007.ledger.domain.model.Money;

/**
 * Explicit three-way outcome of a payment submission. A sealed interface
 * makes the switch-over exhaustive at compile time — the caller cannot
 * forget to handle a case, and if a new case is added the compiler flags
 * every switch that missed the update.
 */
public sealed interface PostPaymentResult {

    /**
     * A new ledger entry was appended. Downstream will hear about it via the
     * {@code PaymentPostedEvent} on the outbox.
     */
    record PostedNew(LedgerEntryId ledgerEntryId, Money amount) implements PostPaymentResult { }

    /**
     * The idempotency key was already known — this is a duplicate submission
     * (retried Kafka message, network flake retry, etc.). The previously
     * produced entry id is returned so the caller can reference it, but no
     * new entry was written and no event was published.
     */
    record AlreadyPosted(LedgerEntryId ledgerEntryId) implements PostPaymentResult { }

    /**
     * The payment failed a business check (unknown account, currency mismatch,
     * insufficient funds, ...). A {@code PaymentFailedEvent} is published so
     * downstream systems (notifications, fraud, ops) can react.
     */
    record Rejected(FailureReason reason, String detail) implements PostPaymentResult { }
}
