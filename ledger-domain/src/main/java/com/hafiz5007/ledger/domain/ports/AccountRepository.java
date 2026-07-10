package com.hafiz5007.ledger.domain.ports;

import com.hafiz5007.ledger.domain.model.Account;
import com.hafiz5007.ledger.domain.model.AccountId;

import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findById(AccountId id);
    void save(Account account);
}
