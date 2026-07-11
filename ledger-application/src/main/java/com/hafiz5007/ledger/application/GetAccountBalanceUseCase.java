package com.hafiz5007.ledger.application;

import com.hafiz5007.ledger.domain.model.Account;
import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.Money;
import com.hafiz5007.ledger.domain.ports.AccountRepository;
import com.hafiz5007.ledger.domain.ports.LedgerEntryStore;

import java.util.Objects;
import java.util.Optional;

/**
 * Read-side use case. Deliberately does not compute the balance itself —
 * it delegates to {@link LedgerEntryStore#balanceOf} which is either a
 * fast projection read (production) or a full sum-of-postings scan
 * (in-memory tests). The domain contract lets the two coexist without
 * this class caring which is behind the port.
 */
public final class GetAccountBalanceUseCase {

    private final AccountRepository accounts;
    private final LedgerEntryStore ledger;

    public GetAccountBalanceUseCase(AccountRepository accounts, LedgerEntryStore ledger) {
        this.accounts = Objects.requireNonNull(accounts);
        this.ledger   = Objects.requireNonNull(ledger);
    }

    public Optional<AccountBalance> execute(AccountId accountId) {
        Optional<Account> account = accounts.findById(accountId);
        if (account.isEmpty()) return Optional.empty();
        Money balance = ledger.balanceOf(accountId);
        return Optional.of(new AccountBalance(account.get(), balance));
    }

    public record AccountBalance(Account account, Money balance) { }
}
