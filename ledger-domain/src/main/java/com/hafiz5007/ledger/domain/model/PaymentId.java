package com.hafiz5007.ledger.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PaymentId(UUID value) {
    public PaymentId { Objects.requireNonNull(value, "value required"); }
    public static PaymentId newId() { return new PaymentId(UUID.randomUUID()); }
    public static PaymentId of(String uuid) { return new PaymentId(UUID.fromString(uuid)); }
    @Override public String toString() { return value.toString(); }
}
