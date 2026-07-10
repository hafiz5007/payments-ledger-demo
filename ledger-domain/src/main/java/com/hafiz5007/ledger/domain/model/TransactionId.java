package com.hafiz5007.ledger.domain.model;

import java.util.Objects;

/**
 * Client-supplied idempotency key. Distinct from {@link PaymentId} because
 * a single logical payment can retry many times — every retry carries the
 * same {@code TransactionId}, so the ledger recognises the duplicate and
 * returns the previous result without re-posting.
 * <p>
 * String-typed rather than UUID because clients often use the upstream
 * system's transaction reference (e.g. an ISO 20022 {@code EndToEndId})
 * which is not necessarily a UUID.
 */
public record TransactionId(String value) {
    public TransactionId {
        Objects.requireNonNull(value, "value required");
        if (value.isBlank()) throw new IllegalArgumentException("TransactionId must not be blank");
        if (value.length() > 64) throw new IllegalArgumentException("TransactionId max length is 64 chars");
    }

    @Override public String toString() { return value; }
}
