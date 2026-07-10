package com.hafiz5007.ledger.domain;

import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.Money;
import com.hafiz5007.ledger.domain.model.PaymentId;
import com.hafiz5007.ledger.domain.model.PaymentInstruction;
import com.hafiz5007.ledger.domain.model.PostingSide;
import com.hafiz5007.ledger.domain.model.TransactionId;
import com.hafiz5007.ledger.domain.ports.Clock;
import com.hafiz5007.ledger.domain.service.DoubleEntryValidator;
import com.hafiz5007.ledger.domain.service.LedgerPoster;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerPosterTest {

    private final Instant fixedNow = Instant.parse("2026-07-08T12:00:00Z");
    private final Clock clock = () -> fixedNow;
    private final LedgerPoster poster = new LedgerPoster(new DoubleEntryValidator(), clock);

    private final AccountId payer   = AccountId.newId();
    private final AccountId payee   = AccountId.newId();

    @Test void producesTwoPostings_debitFromCreditTo() {
        var instruction = new PaymentInstruction(
            PaymentId.newId(),
            new TransactionId("client-123"),
            payer, payee,
            Money.of("42.50", "GBP"),
            "invoice 456"
        );

        var entry = poster.post(instruction);

        assertThat(entry.postings()).hasSize(2);
        assertThat(entry.postings()).extracting("accountId")
            .containsExactly(payer, payee);
        assertThat(entry.postings())
            .filteredOn(p -> p.accountId().equals(payer))
            .allMatch(p -> p.side() == PostingSide.DEBIT);
        assertThat(entry.postings())
            .filteredOn(p -> p.accountId().equals(payee))
            .allMatch(p -> p.side() == PostingSide.CREDIT);
    }

    @Test void stampsOccurredAtFromInjectedClock() {
        var instruction = new PaymentInstruction(
            PaymentId.newId(),
            new TransactionId("client-124"),
            payer, payee,
            Money.of("5.00", "GBP"),
            "test"
        );

        var entry = poster.post(instruction);

        assertThat(entry.occurredAtUtc()).isEqualTo(fixedNow);
    }

    @Test void transactionIdPropagatesToEntry() {
        var txId = new TransactionId("upstream-endToEndId-9876");
        var instruction = new PaymentInstruction(
            PaymentId.newId(),
            txId,
            payer, payee,
            Money.of("1.00", "GBP"),
            "prop"
        );

        var entry = poster.post(instruction);

        assertThat(entry.transactionId()).isEqualTo(txId);
    }
}
