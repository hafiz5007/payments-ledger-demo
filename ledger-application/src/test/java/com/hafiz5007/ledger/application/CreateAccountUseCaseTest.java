package com.hafiz5007.ledger.application;

import com.hafiz5007.ledger.application.fakes.LedgerTestFixture;
import com.hafiz5007.ledger.domain.event.AccountCreatedEvent;
import com.hafiz5007.ledger.domain.model.AccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class CreateAccountUseCaseTest {

    private LedgerTestFixture fx;

    @BeforeEach void setUp() { fx = new LedgerTestFixture(); }

    @Test void createsAccount_persistsIt_publishesEvent() {
        var account = fx.createAccount.execute(new CreateAccountCommand(
            "alice", AccountType.LIABILITY, Currency.getInstance("GBP")));

        assertThat(account.id()).isNotNull();
        assertThat(account.name()).isEqualTo("alice");
        assertThat(account.type()).isEqualTo(AccountType.LIABILITY);
        assertThat(account.currency().getCurrencyCode()).isEqualTo("GBP");
        assertThat(account.active()).isTrue();

        assertThat(fx.accounts.findById(account.id())).contains(account);
        assertThat(fx.outbox.ofType(AccountCreatedEvent.class)).hasSize(1);
    }

    @Test void multipleAccounts_dontClash() {
        var a = fx.createAccount.execute(new CreateAccountCommand("a", AccountType.LIABILITY, Currency.getInstance("GBP")));
        var b = fx.createAccount.execute(new CreateAccountCommand("b", AccountType.LIABILITY, Currency.getInstance("GBP")));

        assertThat(a.id()).isNotEqualTo(b.id());
        assertThat(fx.outbox.ofType(AccountCreatedEvent.class)).hasSize(2);
    }
}
