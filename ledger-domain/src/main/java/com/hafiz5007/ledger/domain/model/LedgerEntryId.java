package com.hafiz5007.ledger.domain.model;

import java.util.Objects;
import java.util.UUID;

public record LedgerEntryId(UUID value) {
    public LedgerEntryId { Objects.requireNonNull(value, "value required"); }
    public static LedgerEntryId newId() { return new LedgerEntryId(UUID.randomUUID()); }
    @Override public String toString() { return value.toString(); }
}
