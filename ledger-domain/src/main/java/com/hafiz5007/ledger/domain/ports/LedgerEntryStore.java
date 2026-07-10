package com.hafiz5007.ledger.domain.ports;

import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.LedgerEntry;
import com.hafiz5007.ledger.domain.model.Money;

import java.util.List;

/**
 * Append-only store for {@link LedgerEntry}. Entries are never modified once
 * written — that's what makes the ledger auditable.
 * <p>
 * Balance-by-account is a derived read; the infrastructure implementation
 * can back it with a projection table for O(1) reads instead of scanning
 * postings every time.
 */
public interface LedgerEntryStore {

    /**
     * Append an entry. Implementations must be atomic — if the write fails
     * halfway, no half-entry ends up persisted. In the Postgres implementation
     * this means the entry write, the posting inserts, and the balance
     * projection update all sit in the same transaction.
     */
    void append(LedgerEntry entry);

    /**
     * Current balance for an account, derived from the sum of its postings.
     * Never trusts a client-supplied cache — always reads the store.
     */
    Money balanceOf(AccountId accountId);

    /**
     * All entries touching this account, newest first. Paginated in the
     * infrastructure implementation; the domain contract doesn't specify a
     * page size because pagination is a display concern.
     */
    List<LedgerEntry> entriesFor(AccountId accountId, int limit);
}
