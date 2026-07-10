package com.hafiz5007.ledger.domain.model;

/**
 * Which side of the double-entry a posting sits on. Every entry has at least
 * one debit and one credit, and the sum of debits equals the sum of credits.
 * <p>
 * Naming is unavoidably historical — "debit" and "credit" have nothing to do
 * with card networks. They're just labels for the two sides of the T-account.
 */
public enum PostingSide {
    DEBIT,
    CREDIT;

    public PostingSide opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
