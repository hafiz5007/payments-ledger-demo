package com.hafiz5007.ledger.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed account identifier. Wrapping the UUID in a record stops
 * accidents like passing a {@link PaymentId} where an account id was
 * expected — the compiler catches it.
 */
public record AccountId(UUID value) {
    public AccountId {
        Objects.requireNonNull(value, "value required");
    }

    public static AccountId newId() { return new AccountId(UUID.randomUUID()); }

    public static AccountId of(String uuid) { return new AccountId(UUID.fromString(uuid)); }

    @Override public String toString() { return value.toString(); }
}
