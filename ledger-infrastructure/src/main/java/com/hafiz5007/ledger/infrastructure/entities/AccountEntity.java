package com.hafiz5007.ledger.infrastructure.entities;

import com.hafiz5007.ledger.domain.model.Account;
import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/**
 * JPA-mapped account row. Kept as a mutable POJO with the standard
 * JPA no-arg constructor. Conversion to and from the immutable Domain
 * {@link Account} record happens here — no other code in the codebase
 * should reference {@link AccountEntity}.
 */
@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private AccountType type;

    @Column(name = "currency", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;

    @Column(name = "active", nullable = false)
    private boolean active;

    // Required by JPA.
    protected AccountEntity() { }

    public static AccountEntity fromDomain(Account a) {
        var e = new AccountEntity();
        e.id = a.id().value();
        e.name = a.name();
        e.type = a.type();
        e.currencyCode = a.currency().getCurrencyCode();
        e.createdAtUtc = a.createdAtUtc();
        e.active = a.active();
        return e;
    }

    public Account toDomain() {
        return new Account(
            new AccountId(id),
            name,
            type,
            Currency.getInstance(currencyCode),
            createdAtUtc,
            active
        );
    }

    /** Merge a mutation (e.g. deactivation) from the domain object back onto this entity. */
    public void updateFrom(Account a) {
        this.name = a.name();
        this.active = a.active();
    }

    // Getters used by adapters that need one field without hydrating the whole domain object.
    public UUID getId()               { return id; }
    public String getName()           { return name; }
    public AccountType getType()      { return type; }
    public String getCurrencyCode()   { return currencyCode; }
    public Instant getCreatedAtUtc()  { return createdAtUtc; }
    public boolean isActive()         { return active; }
}
