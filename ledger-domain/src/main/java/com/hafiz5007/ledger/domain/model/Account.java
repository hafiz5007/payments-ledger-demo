package com.hafiz5007.ledger.domain.model;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/**
 * A single ledger account. Balance is <em>not</em> stored here — it's derived
 * from summing the postings, which is the whole point of event-sourced
 * ledgers. Persistence keeps the balance as a projection updated in the same
 * transaction as the entry write, so reads stay O(1).
 * <p>
 * We store the currency on the account itself. A single account can only hold
 * one currency; a customer with multi-currency support has one account per
 * currency, which is the normal fintech pattern (Wise, Revolut, etc.).
 */
public record Account(
    AccountId id,
    String name,
    AccountType type,
    Currency currency,
    Instant createdAtUtc,
    boolean active
) {
    public Account {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(name, "name required");
        Objects.requireNonNull(type, "type required");
        Objects.requireNonNull(currency, "currency required");
        Objects.requireNonNull(createdAtUtc, "createdAtUtc required");
        if (name.isBlank()) throw new IllegalArgumentException("Account name must not be blank");
    }

    public Account deactivate() {
        return new Account(id, name, type, currency, createdAtUtc, false);
    }
}
