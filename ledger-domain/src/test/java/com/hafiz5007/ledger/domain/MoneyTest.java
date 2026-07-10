package com.hafiz5007.ledger.domain;

import com.hafiz5007.ledger.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test void addsSameCurrency() {
        var a = Money.of("10.00", "GBP");
        var b = Money.of("5.50",  "GBP");
        assertThat(a.plus(b)).isEqualTo(Money.of("15.50", "GBP"));
    }

    @Test void subtractsSameCurrency() {
        var a = Money.of("10.00", "GBP");
        var b = Money.of("3.25",  "GBP");
        assertThat(a.minus(b)).isEqualTo(Money.of("6.75", "GBP"));
    }

    @Test void addingDifferentCurrenciesThrows() {
        var gbp = Money.of("10.00", "GBP");
        var usd = Money.of("10.00", "USD");
        assertThatThrownBy(() -> gbp.plus(usd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Currency mismatch");
    }

    @Test void normalisesScaleToCurrencyFractionDigits() {
        // "10" and "10.00" GBP represent the same amount and must be equal.
        var withoutScale = new Money(new BigDecimal("10"),    java.util.Currency.getInstance("GBP"));
        var withScale    = new Money(new BigDecimal("10.00"), java.util.Currency.getInstance("GBP"));
        assertThat(withoutScale).isEqualTo(withScale);
    }

    @Test void rejectsExtraPrecision() {
        // GBP has 2 fraction digits. 10.001 is not a legal GBP amount.
        assertThatThrownBy(() -> Money.of("10.001", "GBP"))
            .isInstanceOf(ArithmeticException.class);
    }

    @Test void negateReturnsOpposite() {
        assertThat(Money.of("10.00", "GBP").negate())
            .isEqualTo(Money.of("-10.00", "GBP"));
    }

    @Test void predicates() {
        assertThat(Money.of("10", "GBP").isPositive()).isTrue();
        assertThat(Money.of("-1", "GBP").isNegative()).isTrue();
        assertThat(Money.zero("GBP").isZero()).isTrue();
    }

    @Test void comparisons() {
        assertThat(Money.of("10", "GBP").isGreaterThan(Money.of("5", "GBP"))).isTrue();
        assertThat(Money.of("3", "GBP").isLessThan(Money.of("5", "GBP"))).isTrue();
    }
}
