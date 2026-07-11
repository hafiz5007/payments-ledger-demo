package com.hafiz5007.ledger.application.fakes;

import com.hafiz5007.ledger.domain.model.LedgerEntryId;
import com.hafiz5007.ledger.domain.model.TransactionId;
import com.hafiz5007.ledger.domain.ports.IdempotencyStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<TransactionId, LedgerEntryId> byKey = new HashMap<>();

    @Override
    public void record(TransactionId key, LedgerEntryId ledgerEntryId) {
        if (byKey.containsKey(key)) {
            throw new IllegalStateException(
                "Idempotency key " + key + " already recorded — caller should have checked first");
        }
        byKey.put(key, ledgerEntryId);
    }

    @Override
    public Optional<LedgerEntryId> findExistingEntry(TransactionId key) {
        return Optional.ofNullable(byKey.get(key));
    }

    public int size() { return byKey.size(); }
}
