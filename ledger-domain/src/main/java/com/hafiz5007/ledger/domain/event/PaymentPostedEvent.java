package com.hafiz5007.ledger.domain.event;

import com.hafiz5007.ledger.domain.model.LedgerEntryId;
import com.hafiz5007.ledger.domain.model.Money;
import com.hafiz5007.ledger.domain.model.PaymentId;
import com.hafiz5007.ledger.domain.model.TransactionId;

import java.time.Instant;

/**
 * A payment successfully hit the ledger. Downstream consumers (notifications,
 * fraud, reconciliation) subscribe to this via the outbox → Kafka relay in
 * Phase 4.
 */
public record PaymentPostedEvent(
    PaymentId paymentId,
    TransactionId transactionId,
    LedgerEntryId ledgerEntryId,
    Money amount,
    Instant occurredAtUtc
) implements DomainEvent { }
