package com.hafiz5007.ledger.domain.service;

import com.hafiz5007.ledger.domain.model.LedgerEntry;
import com.hafiz5007.ledger.domain.model.Posting;

/**
 * The LedgerEntry constructor already enforces balancing, so this class is
 * mostly a documentation vehicle — but it's the natural home for future
 * domain rules that go beyond "debits = credits" (e.g. "assets can't hold
 * a credit balance", "revenue accounts can't be debited without a matching
 * refund event", etc.).
 * <p>
 * Kept as a separate service so those richer rules have a home when they
 * arrive, rather than piling into the LedgerEntry constructor.
 */
public final class DoubleEntryValidator {

    /**
     * Rejects entries that would violate accounting rules beyond the
     * balancing invariant. Throws with an explanatory message.
     */
    public void validate(LedgerEntry entry) {
        if (hasDuplicateAccounts(entry)) {
            throw new IllegalArgumentException(
                "Entry has multiple postings against the same account. That is legal in "
                + "accounting but is usually a bug in this system — split them into "
                + "separate entries if you really mean it.");
        }
    }

    private boolean hasDuplicateAccounts(LedgerEntry entry) {
        return entry.postings().stream()
            .map(Posting::accountId)
            .distinct()
            .count() != entry.postings().size();
    }
}
