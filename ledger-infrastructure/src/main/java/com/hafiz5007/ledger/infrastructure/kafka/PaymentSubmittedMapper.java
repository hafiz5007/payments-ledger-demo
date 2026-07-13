package com.hafiz5007.ledger.infrastructure.kafka;

import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.Money;
import com.hafiz5007.ledger.domain.model.PaymentId;
import com.hafiz5007.ledger.domain.model.PaymentInstruction;
import com.hafiz5007.ledger.domain.model.TransactionId;

import java.util.UUID;

/**
 * The single place where wire-format {@link PaymentSubmittedMessage} is
 * translated to the domain {@link PaymentInstruction}. Malformed messages
 * throw {@link IllegalArgumentException} — the consumer's error handler
 * routes those to the DLT.
 */
public final class PaymentSubmittedMapper {

    public PaymentInstruction toDomain(PaymentSubmittedMessage message) {
        try {
            return new PaymentInstruction(
                new PaymentId(UUID.fromString(message.paymentId())),
                new TransactionId(message.transactionId()),
                new AccountId(UUID.fromString(message.fromAccountId())),
                new AccountId(UUID.fromString(message.toAccountId())),
                Money.of(message.amount(), message.currency()),
                message.reference()
            );
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException(
                "Malformed PaymentSubmittedMessage: " + ex.getMessage(), ex);
        }
    }
}
