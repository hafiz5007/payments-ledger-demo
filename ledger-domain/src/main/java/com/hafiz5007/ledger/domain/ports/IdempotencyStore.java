package com.hafiz5007.ledger.domain.ports;

import com.hafiz5007.ledger.domain.model.LedgerEntryId;
import com.hafiz5007.ledger.domain.model.TransactionId;

import java.util.Optional;

/**
 * Tracks which {@link TransactionId}s have already been processed and what
 * result they produced. Two duplicate submissions with the same key must
 * produce the same effect — the second call returns the first call's
 * ledger entry id without posting again.
 * <p>
 * This is the "effectively-once" pattern. True exactly-once delivery is
 * impossible in a distributed system; effectively-once is achievable by
 * being idempotent under the client's retry semantics.
 */
public interface IdempotencyStore {

    /**
     * Remember that this transaction was processed and produced this entry.
     * Fails if the key is already present — the caller should have checked
     * {@link #findExistingEntry} first.
     */
    void record(TransactionId key, LedgerEntryId ledgerEntryId);

    Optional<LedgerEntryId> findExistingEntry(TransactionId key);
}
