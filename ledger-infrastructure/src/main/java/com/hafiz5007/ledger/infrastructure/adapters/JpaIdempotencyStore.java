package com.hafiz5007.ledger.infrastructure.adapters;

import com.hafiz5007.ledger.domain.model.LedgerEntryId;
import com.hafiz5007.ledger.domain.model.TransactionId;
import com.hafiz5007.ledger.domain.ports.Clock;
import com.hafiz5007.ledger.domain.ports.IdempotencyStore;
import com.hafiz5007.ledger.infrastructure.entities.IdempotencyKeyEntity;
import com.hafiz5007.ledger.infrastructure.repositories.IdempotencyJpaRepository;

import java.util.Optional;

public final class JpaIdempotencyStore implements IdempotencyStore {

    private final IdempotencyJpaRepository repository;
    private final Clock clock;

    public JpaIdempotencyStore(IdempotencyJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void record(TransactionId key, LedgerEntryId ledgerEntryId) {
        repository.save(new IdempotencyKeyEntity(key.value(), ledgerEntryId.value(), clock.nowUtc()));
    }

    @Override
    public Optional<LedgerEntryId> findExistingEntry(TransactionId key) {
        return repository.findById(key.value())
            .map(e -> new LedgerEntryId(e.ledgerEntryId()));
    }
}
