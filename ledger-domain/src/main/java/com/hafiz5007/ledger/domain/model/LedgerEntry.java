package com.hafiz5007.ledger.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single ledger entry — one atomic accounting event, made up of two or more
 * {@link Posting}s that balance to zero per currency.
 * <p>
 * The balancing invariant is enforced <em>in the constructor</em>. There is
 * no way to construct a LedgerEntry whose debits and credits don't match.
 * Every piece of code downstream — the store, the outbox, the balance view —
 * gets to assume the invariant holds without re-checking.
 * <p>
 * That's the value of "make illegal states unrepresentable" done right in
 * Java: the type system stops you from persisting a broken ledger.
 */
public record LedgerEntry(
    LedgerEntryId id,
    TransactionId transactionId,
    List<Posting> postings,
    Instant occurredAtUtc,
    String description
) {
    public LedgerEntry {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(transactionId, "transactionId required");
        Objects.requireNonNull(postings, "postings required");
        Objects.requireNonNull(occurredAtUtc, "occurredAtUtc required");
        Objects.requireNonNull(description, "description required");

        if (postings.size() < 2) {
            throw new IllegalArgumentException(
                "Ledger entry needs at least two postings; got " + postings.size());
        }
        requireBalanced(postings);
        // Defensive copy so a mutated caller list can't retroactively break the invariant.
        postings = List.copyOf(postings);
    }

    /**
     * Sum of signed amounts per currency must be exactly zero. Multi-currency
     * entries are allowed at the type level (e.g. a hedged forex booking)
     * but each currency's leg must balance on its own — a domain rule that
     * catches "we forgot the FX leg" errors on ingestion.
     */
    private static void requireBalanced(List<Posting> postings) {
        Map<Currency, BigDecimal> byCurrency = new HashMap<>();
        for (Posting p : postings) {
            byCurrency.merge(
                p.amount().currency(),
                p.signedAmountValue(),
                BigDecimal::add);
        }
        for (Map.Entry<Currency, BigDecimal> e : byCurrency.entrySet()) {
            if (e.getValue().signum() != 0) {
                throw new IllegalArgumentException(
                    "Postings do not balance in " + e.getKey().getCurrencyCode()
                    + ": net " + e.getValue().toPlainString()
                    + ". Every entry must have debits = credits per currency.");
            }
        }
    }

    /**
     * Total value moved on one side of the entry (equal to the total on the
     * other side by construction). Useful for reporting and alerts.
     */
    public Money totalPerCurrency(Currency currency) {
        BigDecimal total = postings.stream()
            .filter(p -> p.amount().currency().equals(currency))
            .filter(p -> p.side() == PostingSide.DEBIT)
            .map(p -> p.amount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Money(total, currency);
    }
}
