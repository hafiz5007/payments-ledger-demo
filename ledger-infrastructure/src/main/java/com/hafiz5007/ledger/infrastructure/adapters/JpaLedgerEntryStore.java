package com.hafiz5007.ledger.infrastructure.adapters;

import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.AccountType;
import com.hafiz5007.ledger.domain.model.LedgerEntry;
import com.hafiz5007.ledger.domain.model.Money;
import com.hafiz5007.ledger.domain.model.Posting;
import com.hafiz5007.ledger.domain.model.PostingSide;
import com.hafiz5007.ledger.domain.ports.Clock;
import com.hafiz5007.ledger.domain.ports.LedgerEntryStore;
import com.hafiz5007.ledger.infrastructure.entities.LedgerEntryEntity;
import com.hafiz5007.ledger.infrastructure.entities.PostingEntity;
import com.hafiz5007.ledger.infrastructure.repositories.AccountBalanceJpaRepository;
import com.hafiz5007.ledger.infrastructure.repositories.AccountJpaRepository;
import com.hafiz5007.ledger.infrastructure.repositories.LedgerEntryJpaRepository;
import com.hafiz5007.ledger.infrastructure.repositories.PostingJpaRepository;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

/**
 * Postgres-backed event store. The single-transaction write on {@link #append}
 * inserts three groups of rows:
 * <ol>
 *   <li>The ledger_entries header</li>
 *   <li>N posting rows</li>
 *   <li>N account_balance projection updates (with SELECT ... FOR UPDATE
 *       via {@code AccountBalanceJpaRepository.findByIdForUpdate})</li>
 * </ol>
 * <p>
 * All three must succeed or fail together. That's enforced by
 * {@code @Transactional} on the use case wrapper in Phase 5 — this adapter
 * assumes it's called within a transaction and doesn't manage one itself.
 */
public final class JpaLedgerEntryStore implements LedgerEntryStore {

    private final LedgerEntryJpaRepository ledgerEntries;
    private final PostingJpaRepository postings;
    private final AccountJpaRepository accounts;
    private final AccountBalanceJpaRepository balances;
    private final Clock clock;

    public JpaLedgerEntryStore(LedgerEntryJpaRepository ledgerEntries,
                                PostingJpaRepository postings,
                                AccountJpaRepository accounts,
                                AccountBalanceJpaRepository balances,
                                Clock clock) {
        this.ledgerEntries = ledgerEntries;
        this.postings = postings;
        this.accounts = accounts;
        this.balances = balances;
        this.clock = clock;
    }

    @Override
    public void append(LedgerEntry entry) {
        ledgerEntries.save(LedgerEntryEntity.headerOf(entry));

        for (Posting posting : entry.postings()) {
            postings.save(PostingEntity.from(entry.id().value(), posting));
            applyToBalanceProjection(posting);
        }
    }

    @Override
    public Money balanceOf(AccountId accountId) {
        var balance = balances.findById(accountId.value())
            .orElseThrow(() -> new IllegalStateException(
                "No balance row for account " + accountId
                + " — the account_balance projection row is bootstrapped when the account is created"));
        return balance.toMoney();
    }

    @Override
    public List<LedgerEntry> entriesFor(AccountId accountId, int limit) {
        // We fetch the entries first, then their postings in a follow-up query
        // per entry. In Phase 5 we'll swap this for a single JPQL join + batching
        // if it shows up in profiling.
        var page = ledgerEntries.findByAccountId(accountId.value(), PageRequest.of(0, limit));
        return page.stream()
            .map(this::hydrate)
            .toList();
    }

    private LedgerEntry hydrate(LedgerEntryEntity header) {
        var rows = postings.findByLedgerEntryId(header.id());
        var domainPostings = rows.stream().map(PostingEntity::toDomain).toList();
        return new LedgerEntry(
            header.asDomainId(),
            header.asTransactionId(),
            domainPostings,
            header.occurredAtUtc(),
            header.description()
        );
    }

    /**
     * Update the balance projection for the account touched by this posting.
     * Owner-perspective: for accounts with debit-normal balance (ASSET,
     * EXPENSE) a debit adds to the balance and a credit subtracts. For
     * credit-normal accounts (LIABILITY, EQUITY, REVENUE) it's the other way.
     * <p>
     * The {@code findByIdForUpdate} query takes a row-level lock, so
     * concurrent posters on the same account queue up rather than losing
     * writes.
     */
    private void applyToBalanceProjection(Posting posting) {
        var account = accounts.findById(posting.accountId().value())
            .orElseThrow(() -> new IllegalStateException(
                "Account " + posting.accountId() + " referenced by posting but not found"));

        var balance = balances.findByIdForUpdate(posting.accountId().value())
            .orElseThrow(() -> new IllegalStateException(
                "Balance row missing for account " + posting.accountId()));

        AccountType type = account.getType();
        BigDecimal delta = posting.side() == type.normalBalanceSide()
            ? posting.amount().amount()
            : posting.amount().amount().negate();
        balance.add(delta, clock.nowUtc());
        balances.save(balance);
    }
}
