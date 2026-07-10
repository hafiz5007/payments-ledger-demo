package com.hafiz5007.ledger.domain.model;

/**
 * The five kinds of account in double-entry accounting. Their "normal balance
 * side" determines whether a positive balance is stored as a debit or credit
 * total — assets/expenses increase on debits, liabilities/equity/revenue
 * increase on credits.
 * <p>
 * This distinction rarely matters at the domain level (balancing an entry
 * is symmetric — debits = credits regardless of which side is "normal") but
 * it matters for reporting: an asset account with a credit balance is
 * unusual and worth flagging.
 */
public enum AccountType {
    ASSET     (PostingSide.DEBIT),   // customer balance, cash, receivables
    LIABILITY (PostingSide.CREDIT),  // deposits, payables
    EQUITY    (PostingSide.CREDIT),  // retained earnings
    REVENUE   (PostingSide.CREDIT),  // fees earned
    EXPENSE   (PostingSide.DEBIT);   // interest paid, refunds

    private final PostingSide normalBalanceSide;

    AccountType(PostingSide normalBalanceSide) {
        this.normalBalanceSide = normalBalanceSide;
    }

    public PostingSide normalBalanceSide() { return normalBalanceSide; }
}
