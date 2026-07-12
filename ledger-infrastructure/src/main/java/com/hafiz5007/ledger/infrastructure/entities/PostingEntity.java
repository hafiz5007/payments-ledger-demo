package com.hafiz5007.ledger.infrastructure.entities;

import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.Money;
import com.hafiz5007.ledger.domain.model.Posting;
import com.hafiz5007.ledger.domain.model.PostingSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

/**
 * One side of a double-entry, mapped to a row in {@code postings}.
 * The primary key is a database-generated BIGSERIAL — postings are
 * looked up by ({@code account_id}, {@code ledger_entry_id}), not by
 * their own id.
 */
@Entity
@Table(name = "postings")
public class PostingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 6)
    private PostingSide side;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currencyCode;

    protected PostingEntity() { }

    public static PostingEntity from(UUID ledgerEntryId, Posting p) {
        var e = new PostingEntity();
        e.ledgerEntryId = ledgerEntryId;
        e.accountId     = p.accountId().value();
        e.side          = p.side();
        e.amount        = p.amount().amount();
        e.currencyCode  = p.amount().currency().getCurrencyCode();
        return e;
    }

    public Posting toDomain() {
        return new Posting(
            new AccountId(accountId),
            side,
            new Money(amount, Currency.getInstance(currencyCode))
        );
    }

    public UUID ledgerEntryId() { return ledgerEntryId; }
    public UUID accountId() { return accountId; }
    public PostingSide side() { return side; }
    public BigDecimal amount() { return amount; }
    public String currencyCode() { return currencyCode; }
}
