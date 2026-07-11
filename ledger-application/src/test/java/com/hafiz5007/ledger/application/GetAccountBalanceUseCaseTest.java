package com.hafiz5007.ledger.application;

import com.hafiz5007.ledger.application.fakes.LedgerTestFixture;
import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GetAccountBalanceUseCaseTest {

    private LedgerTestFixture fx;

    @BeforeEach void setUp() { fx = new LedgerTestFixture(); }

    @Test void unknownAccount_returnsEmpty() {
        var result = fx.getBalance.execute(AccountId.newId());
        assertThat(result).isEmpty();
    }

    @Test void newAccount_zeroBalance() {
        var alice = fx.seedLiabilityAccount("alice", "GBP");

        var result = fx.getBalance.execute(alice.id());

        assertThat(result).isPresent();
        assertThat(result.get().balance()).isEqualTo(Money.zero("GBP"));
    }

    @Test void accountWithOpeningBalance() {
        var alice = fx.seedLiabilityAccount("alice", "GBP");
        fx.giveOpeningBalance(alice, Money.of("250.00", "GBP"));

        var result = fx.getBalance.execute(alice.id());

        assertThat(result).isPresent();
        assertThat(result.get().balance()).isEqualTo(Money.of("250.00", "GBP"));
        assertThat(result.get().account()).isEqualTo(alice);
    }
}
