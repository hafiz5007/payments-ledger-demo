package com.hafiz5007.ledger.domain;

import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.LedgerEntry;
import com.hafiz5007.ledger.domain.model.LedgerEntryId;
import com.hafiz5007.ledger.domain.model.Money;
import com.hafiz5007.ledger.domain.model.Posting;
import com.hafiz5007.ledger.domain.model.TransactionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerEntryTest {

    private final AccountId acctA = AccountId.newId();
    private final AccountId acctB = AccountId.newId();
    private final AccountId acctC = AccountId.newId();

    @Test void constructsBalancedTwoPostingEntry() {
        var entry = new LedgerEntry(
            LedgerEntryId.newId(),
            new TransactionId("tx-1"),
            List.of(
                Posting.debit(acctA,  Money.of("10.00", "GBP")),
                Posting.credit(acctB, Money.of("10.00", "GBP"))
            ),
            Instant.parse("2026-07-08T12:00:00Z"),
            "test"
        );
        assertThat(entry.postings()).hasSize(2);
    }

    @Test void constructsBalancedMultiPostingEntry() {
        // A three-way entry — cash from A, half to B, half to C.
        var entry = new LedgerEntry(
            LedgerEntryId.newId(),
            new TransactionId("tx-2"),
            List.of(
                Posting.debit(acctA,  Money.of("10.00", "GBP")),
                Posting.credit(acctB, Money.of("5.00",  "GBP")),
                Posting.credit(acctC, Money.of("5.00",  "GBP"))
            ),
            Instant.parse("2026-07-08T12:00:00Z"),
            "split"
        );
        assertThat(entry.postings()).hasSize(3);
    }

    @Test void unbalancedEntryThrows() {
        assertThatThrownBy(() -> new LedgerEntry(
            LedgerEntryId.newId(),
            new TransactionId("tx-3"),
            List.of(
                Posting.debit(acctA,  Money.of("10.00", "GBP")),
                Posting.credit(acctB, Money.of("9.99",  "GBP"))
            ),
            Instant.parse("2026-07-08T12:00:00Z"),
            "off-by-a-penny"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Postings do not balance");
    }

    @Test void singlePostingThrows() {
        assertThatThrownBy(() -> new LedgerEntry(
            LedgerEntryId.newId(),
            new TransactionId("tx-4"),
            List.of(Posting.debit(acctA, Money.of("10.00", "GBP"))),
            Instant.parse("2026-07-08T12:00:00Z"),
            "solo"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("at least two postings");
    }

    @Test void multiCurrencyMustBalancePerCurrency() {
        // GBP leg balances, USD leg doesn't → reject.
        assertThatThrownBy(() -> new LedgerEntry(
            LedgerEntryId.newId(),
            new TransactionId("tx-5"),
            List.of(
                Posting.debit(acctA,  Money.of("10.00", "GBP")),
                Posting.credit(acctB, Money.of("10.00", "GBP")),
                Posting.debit(acctA,  Money.of("5.00",  "USD"))
                // Missing USD credit leg — unbalanced.
            ),
            Instant.parse("2026-07-08T12:00:00Z"),
            "half-forex"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("USD");
    }

    @Test void postingListIsDefensivelyCopied() {
        var postings = new java.util.ArrayList<>(List.of(
            Posting.debit(acctA,  Money.of("10.00", "GBP")),
            Posting.credit(acctB, Money.of("10.00", "GBP"))
        ));
        var entry = new LedgerEntry(
            LedgerEntryId.newId(),
            new TransactionId("tx-6"),
            postings,
            Instant.parse("2026-07-08T12:00:00Z"),
            "defensive"
        );
        // Mutating the caller list must not affect the entry.
        postings.add(Posting.debit(acctC, Money.of("999.00", "GBP")));
        assertThat(entry.postings()).hasSize(2);
    }
}
