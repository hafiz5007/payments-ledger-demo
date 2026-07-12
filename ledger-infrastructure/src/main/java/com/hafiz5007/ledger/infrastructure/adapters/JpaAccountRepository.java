package com.hafiz5007.ledger.infrastructure.adapters;

import com.hafiz5007.ledger.domain.model.Account;
import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.ports.AccountRepository;
import com.hafiz5007.ledger.domain.ports.Clock;
import com.hafiz5007.ledger.infrastructure.entities.AccountBalanceEntity;
import com.hafiz5007.ledger.infrastructure.entities.AccountEntity;
import com.hafiz5007.ledger.infrastructure.repositories.AccountBalanceJpaRepository;
import com.hafiz5007.ledger.infrastructure.repositories.AccountJpaRepository;

import java.util.Optional;

/**
 * Bridges the domain {@link AccountRepository} port to the Spring Data JPA
 * repository. When a new account is saved, its balance projection row is
 * created at zero — subsequent postings assume the row already exists.
 */
public final class JpaAccountRepository implements AccountRepository {

    private final AccountJpaRepository accounts;
    private final AccountBalanceJpaRepository balances;
    private final Clock clock;

    public JpaAccountRepository(AccountJpaRepository accounts,
                                AccountBalanceJpaRepository balances,
                                Clock clock) {
        this.accounts = accounts;
        this.balances = balances;
        this.clock = clock;
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return accounts.findById(id.value()).map(AccountEntity::toDomain);
    }

    @Override
    public void save(Account account) {
        var existing = accounts.findById(account.id().value());
        if (existing.isPresent()) {
            existing.get().updateFrom(account);
            accounts.save(existing.get());
        } else {
            accounts.save(AccountEntity.fromDomain(account));
            // Bootstrap the balance projection row at zero.
            balances.save(AccountBalanceEntity.zeroBalance(
                account.id().value(),
                account.currency().getCurrencyCode(),
                clock.nowUtc()));
        }
    }
}
