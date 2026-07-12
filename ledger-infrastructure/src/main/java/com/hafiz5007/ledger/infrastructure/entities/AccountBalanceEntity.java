package com.hafiz5007.ledger.infrastructure.entities;

import com.hafiz5007.ledger.domain.model.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/**
 * The balance projection. One row per account. Updated in the same JPA
 * transaction as the underlying postings so a read never sees a mid-flight
 * inconsistency.
 * <p>
 * Kept as a separate table (not a materialized view) so the update can be
 * driven by application code inside the same {@code @Transactional} boundary.
 * A nightly reconciliation job (roadmap) compares this against
 * SUM(postings.signed_amount) grouped by account and alerts on any drift.
 */
@Entity
@Table(name = "account_balances")
public class AccountBalanceEntity {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountBalanceEntity() { }

    public static AccountBalanceEntity zeroBalance(UUID accountId, String currencyCode, Instant now) {
        var e = new AccountBalanceEntity();
        e.accountId = accountId;
        e.amount = BigDecimal.ZERO;
        e.currencyCode = currencyCode;
        e.updatedAt = now;
        return e;
    }

    public void add(BigDecimal delta, Instant now) {
        this.amount = this.amount.add(delta);
        this.updatedAt = now;
    }

    public Money toMoney() {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public UUID accountId() { return accountId; }
    public BigDecimal amount() { return amount; }
    public String currencyCode() { return currencyCode; }
}
