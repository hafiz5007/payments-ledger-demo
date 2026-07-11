package com.hafiz5007.ledger.application;

import com.hafiz5007.ledger.domain.event.AccountCreatedEvent;
import com.hafiz5007.ledger.domain.model.Account;
import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.ports.AccountRepository;
import com.hafiz5007.ledger.domain.ports.Clock;
import com.hafiz5007.ledger.domain.ports.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Provisioning use case. Persists the {@link Account} and publishes an
 * {@link AccountCreatedEvent} so downstream systems (KYC, notifications,
 * customer profile) can react.
 */
public final class CreateAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateAccountUseCase.class);

    private final AccountRepository accounts;
    private final OutboxPublisher outbox;
    private final Clock clock;

    public CreateAccountUseCase(AccountRepository accounts, OutboxPublisher outbox, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts);
        this.outbox   = Objects.requireNonNull(outbox);
        this.clock    = Objects.requireNonNull(clock);
    }

    public Account execute(CreateAccountCommand command) {
        var account = new Account(
            AccountId.newId(),
            command.name(),
            command.type(),
            command.currency(),
            clock.nowUtc(),
            true
        );
        accounts.save(account);
        outbox.publish(new AccountCreatedEvent(
            account.id(),
            account.name(),
            account.type(),
            account.currency(),
            clock.nowUtc()
        ));
        log.info("Created account {} type={} currency={}",
            account.id(), account.type(), account.currency().getCurrencyCode());
        return account;
    }
}
