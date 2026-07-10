package com.hafiz5007.ledger.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A single side of a double-entry ledger entry. Records that a specific
 * account was either debited or credited by a specific amount.
 * <p>
 * The amount is always non-negative — the "which side" information lives in
 * {@link #side()}, not in the sign of the amount. {@link #signedAmount()}
 * projects that back to a signed value for balance calculations.
 */
public record Posting(AccountId accountId, PostingSide side, Money amount) {

    public Posting {
        Objects.requireNonNull(accountId, "accountId required");
        Objects.requireNonNull(side, "side required");
        Objects.requireNonNull(amount, "amount required");
        if (amount.isNegative()) {
            throw new IllegalArgumentException(
                "Posting amount must be non-negative; use PostingSide to encode direction. Got " + amount);
        }
        if (amount.isZero()) {
            throw new IllegalArgumentException("Zero-amount postings are not allowed");
        }
    }

    /**
     * Convenience factories that let the reading code stay close to accounting
     * conventions: {@code Posting.debit(cashAccount, tenPounds)}.
     */
    public static Posting debit(AccountId accountId, Money amount) {
        return new Posting(accountId, PostingSide.DEBIT, amount);
    }

    public static Posting credit(AccountId accountId, Money amount) {
        return new Posting(accountId, PostingSide.CREDIT, amount);
    }

    /**
     * Amount as it would contribute to a running sum for balance-checking.
     * Debits count as positive, credits as negative — so a balanced entry's
     * signed sum is zero.
     */
    public Money signedAmount() {
        return side == PostingSide.DEBIT ? amount : amount.negate();
    }

    /**
     * The underlying BigDecimal signed value, useful when aggregating across
     * currencies via {@link java.util.Currency} keys.
     */
    public BigDecimal signedAmountValue() {
        return signedAmount().amount();
    }
}
