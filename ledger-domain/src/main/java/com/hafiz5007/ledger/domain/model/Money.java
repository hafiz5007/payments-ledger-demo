package com.hafiz5007.ledger.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable monetary amount with a currency.
 * <p>
 * Every arithmetic operation is currency-safe: adding GBP to USD throws
 * immediately. The scale is normalised to the currency's default fraction
 * digits so a stored value of "10" and a stored value of "10.00" represent
 * the same amount when compared.
 * <p>
 * Uses {@link BigDecimal} — never a floating-point type. If you find yourself
 * reaching for {@code double} to represent money anywhere in this codebase,
 * that's a bug.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount required");
        Objects.requireNonNull(currency, "currency required");
        // Normalise scale so equals() works predictably across "10" and "10.00".
        // UNNECESSARY throws if the caller passed extra precision (e.g. 10.001 GBP).
        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits >= 0) {
            amount = amount.setScale(fractionDigits, RoundingMode.UNNECESSARY);
        }
    }

    // ---- factory helpers ----------------------------------------------------

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money of(long amount, String currencyCode) {
        return new Money(BigDecimal.valueOf(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(String currencyCode) {
        return new Money(BigDecimal.ZERO, Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    // ---- arithmetic ---------------------------------------------------------

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public Money abs() {
        return isNegative() ? negate() : this;
    }

    // ---- predicates ---------------------------------------------------------

    public boolean isZero() { return amount.signum() == 0; }
    public boolean isPositive() { return amount.signum() > 0; }
    public boolean isNegative() { return amount.signum() < 0; }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    // ---- internals ----------------------------------------------------------

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Currency mismatch: " + currency.getCurrencyCode()
                + " vs " + other.currency.getCurrencyCode()
                + ". Cross-currency conversion is not a domain-layer operation.");
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
