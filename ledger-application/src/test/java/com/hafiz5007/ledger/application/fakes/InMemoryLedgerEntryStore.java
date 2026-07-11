package com.hafiz5007.ledger.application.fakes;

import com.hafiz5007.ledger.domain.model.Account;
import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.LedgerEntry;
import com.hafiz5007.ledger.domain.model.Money;
import com.hafiz5007.ledger.domain.model.Posting;
import com.hafiz5007.ledger.domain.ports.AccountRepository;
import com.hafiz5007.ledger.domain.ports.LedgerEntryStore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * In-memory event store. Balance is computed on demand by summing signed
 * postings — a Postgres implementation would keep a projection for O(1)
 * reads. The {@link AccountRepository} lookup is used to determine the
 * owner-perspective sign: for LIABILITY / EQUITY / REVENUE accounts the
 * balance goes up on credits; for ASSET / EXPENSE it goes up on debits.
 */
public final class InMemoryLedgerEntryStore implements LedgerEntryStore {

    private final List<LedgerEntry> entries = new ArrayList<>();
    private final AccountRepository accounts;

    public InMemoryLedgerEntryStore(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public void append(LedgerEntry entry) {
        entries.add(entry);
    }

    @Override
    public Money balanceOf(AccountId accountId) {
        Account account = accounts.findById(accountId)
            .orElseThrow(() -> new IllegalStateException("Unknown account " + accountId));

        BigDecimal balance = BigDecimal.ZERO;
        for (LedgerEntry entry : entries) {
            for (Posting p : entry.postings()) {
                if (!p.accountId().equals(accountId)) continue;
                if (!p.amount().currency().equals(account.currency())) continue;

                // Owner-perspective sign: does this posting increase or decrease
                // what the owner has? Depends on account type.
                boolean increases = p.side() == account.type().normalBalanceSide();
                BigDecimal delta = increases
                    ? p.amount().amount()
                    : p.amount().amount().negate();
                balance = balance.add(delta);
            }
        }
        return new Money(balance, account.currency());
    }

    @Override
    public List<LedgerEntry> entriesFor(AccountId accountId, int limit) {
        return entries.stream()
            .filter(e -> e.postings().stream().anyMatch(p -> p.accountId().equals(accountId)))
            .sorted(Comparator.comparing(LedgerEntry::occurredAtUtc).reversed())
            .limit(limit)
            .toList();
    }

    public int size() { return entries.size(); }
}
