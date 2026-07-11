package com.hafiz5007.ledger.application.fakes;

import com.hafiz5007.ledger.domain.model.Account;
import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.ports.AccountRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryAccountRepository implements AccountRepository {

    private final Map<AccountId, Account> accounts = new HashMap<>();

    @Override
    public Optional<Account> findById(AccountId id) {
        return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public void save(Account account) {
        accounts.put(account.id(), account);
    }
}
