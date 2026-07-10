package com.hafiz5007.ledger.domain.service;

import com.hafiz5007.ledger.domain.model.LedgerEntry;
import com.hafiz5007.ledger.domain.model.LedgerEntryId;
import com.hafiz5007.ledger.domain.model.PaymentInstruction;
import com.hafiz5007.ledger.domain.model.Posting;
import com.hafiz5007.ledger.domain.ports.Clock;

import java.util.List;
import java.util.Objects;

/**
 * Turns a {@link PaymentInstruction} into a balanced {@link LedgerEntry}.
 * <p>
 * Modelled as debit-from, credit-to: the source account is debited (its
 * balance decreases if it's a liability like a customer deposit) and the
 * destination account is credited. The {@link DoubleEntryValidator} then
 * has one last look before it's persisted.
 * <p>
 * Purely a mapping service; no I/O. Every side effect happens in the use
 * case that calls this.
 */
public final class LedgerPoster {

    private final DoubleEntryValidator validator;
    private final Clock clock;

    public LedgerPoster(DoubleEntryValidator validator, Clock clock) {
        this.validator = Objects.requireNonNull(validator);
        this.clock = Objects.requireNonNull(clock);
    }

    public LedgerEntry post(PaymentInstruction instruction) {
        var entry = new LedgerEntry(
            LedgerEntryId.newId(),
            instruction.idempotencyKey(),
            List.of(
                Posting.debit(instruction.fromAccountId(), instruction.amount()),
                Posting.credit(instruction.toAccountId(), instruction.amount())
            ),
            clock.nowUtc(),
            "Payment " + instruction.paymentId() + " ref=" + instruction.reference()
        );
        validator.validate(entry);
        return entry;
    }
}
