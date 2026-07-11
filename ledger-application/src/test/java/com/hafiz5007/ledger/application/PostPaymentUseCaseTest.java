package com.hafiz5007.ledger.application;

import com.hafiz5007.ledger.application.fakes.LedgerTestFixture;
import com.hafiz5007.ledger.domain.event.PaymentFailedEvent;
import com.hafiz5007.ledger.domain.event.PaymentFailedEvent.FailureReason;
import com.hafiz5007.ledger.domain.event.PaymentPostedEvent;
import com.hafiz5007.ledger.domain.model.Money;
import com.hafiz5007.ledger.domain.model.PaymentId;
import com.hafiz5007.ledger.domain.model.PaymentInstruction;
import com.hafiz5007.ledger.domain.model.TransactionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostPaymentUseCaseTest {

    private LedgerTestFixture fx;

    @BeforeEach void setUp() { fx = new LedgerTestFixture(); }

    private PaymentInstruction instruction(String txId, Money amount,
                                            com.hafiz5007.ledger.domain.model.Account from,
                                            com.hafiz5007.ledger.domain.model.Account to) {
        return new PaymentInstruction(
            PaymentId.newId(),
            new TransactionId(txId),
            from.id(),
            to.id(),
            amount,
            "test-ref"
        );
    }

    // ── Happy path ─────────────────────────────────────────────────────────

    @Test void happyPath_writesEntry_recordsIdempotency_publishesEvent() {
        var alice = fx.seedLiabilityAccount("alice", "GBP");
        var bob   = fx.seedLiabilityAccount("bob",   "GBP");
        fx.giveOpeningBalance(alice, Money.of("100.00", "GBP"));

        var result = fx.postPayment.execute(
            instruction("tx-1", Money.of("30.00", "GBP"), alice, bob));

        assertThat(result).isInstanceOf(PostPaymentResult.PostedNew.class);
        assertThat(fx.ledger.size()).isEqualTo(2);      // opening + payment
        assertThat(fx.idempotency.size()).isEqualTo(1);
        assertThat(fx.outbox.ofType(PaymentPostedEvent.class)).hasSize(1);
    }

    @Test void happyPath_balancesUpdate() {
        var alice = fx.seedLiabilityAccount("alice", "GBP");
        var bob   = fx.seedLiabilityAccount("bob",   "GBP");
        fx.giveOpeningBalance(alice, Money.of("100.00", "GBP"));

        fx.postPayment.execute(instruction("tx-1", Money.of("30.00", "GBP"), alice, bob));

        assertThat(fx.ledger.balanceOf(alice.id())).isEqualTo(Money.of("70.00", "GBP"));
        assertThat(fx.ledger.balanceOf(bob.id())).isEqualTo(Money.of("30.00", "GBP"));
    }

    // ── Idempotency ─────────────────────────────────────────────────────────

    @Test void duplicateSubmission_returnsAlreadyPosted_doesNotDoubleWrite() {
        var alice = fx.seedLiabilityAccount("alice", "GBP");
        var bob   = fx.seedLiabilityAccount("bob",   "GBP");
        fx.giveOpeningBalance(alice, Money.of("100.00", "GBP"));

        var first  = fx.postPayment.execute(instruction("tx-1", Money.of("30.00", "GBP"), alice, bob));
        var second = fx.postPayment.execute(instruction("tx-1", Money.of("30.00", "GBP"), alice, bob));

        assertThat(first).isInstanceOf(PostPaymentResult.PostedNew.class);
        assertThat(second).isInstanceOf(PostPaymentResult.AlreadyPosted.class);

        // Second submission returns the SAME entry id — that's the contract.
        var firstId  = ((PostPaymentResult.PostedNew) first).ledgerEntryId();
        var secondId = ((PostPaymentResult.AlreadyPosted) second).ledgerEntryId();
        assertThat(secondId).isEqualTo(firstId);

        // No second entry written, no second event emitted.
        assertThat(fx.ledger.size()).isEqualTo(2);   // opening + one payment
        assertThat(fx.outbox.ofType(PaymentPostedEvent.class)).hasSize(1);

        // And Alice's balance is 70, not 40. Double-write would drop it further.
        assertThat(fx.ledger.balanceOf(alice.id())).isEqualTo(Money.of("70.00", "GBP"));
    }

    // ── Rejections ──────────────────────────────────────────────────────────

    @Test void missingFromAccount_rejects_publishesFailure() {
        var bob = fx.seedLiabilityAccount("bob", "GBP");
        var ghost = com.hafiz5007.ledger.domain.model.AccountId.newId();

        var result = fx.postPayment.execute(new PaymentInstruction(
            PaymentId.newId(),
            new TransactionId("tx-1"),
            ghost, bob.id(),
            Money.of("10.00", "GBP"),
            "test"
        ));

        assertRejectedWith(result, FailureReason.ACCOUNT_NOT_FOUND);
        assertThat(fx.outbox.ofType(PaymentFailedEvent.class)).hasSize(1);
        assertThat(fx.ledger.size()).isZero();
    }

    @Test void inactiveFromAccount_rejects() {
        var alice = fx.seedLiabilityAccount("alice", "GBP");
        var bob   = fx.seedLiabilityAccount("bob",   "GBP");
        fx.giveOpeningBalance(alice, Money.of("100.00", "GBP"));
        // Deactivate alice
        fx.accounts.save(alice.deactivate());

        var result = fx.postPayment.execute(
            instruction("tx-1", Money.of("10.00", "GBP"), alice, bob));

        assertRejectedWith(result, FailureReason.ACCOUNT_INACTIVE);
    }

    @Test void currencyMismatch_rejects() {
        var alice = fx.seedLiabilityAccount("alice", "GBP");
        var bob   = fx.seedLiabilityAccount("bob",   "USD");   // different currency
        fx.giveOpeningBalance(alice, Money.of("100.00", "GBP"));

        var result = fx.postPayment.execute(
            instruction("tx-1", Money.of("10.00", "GBP"), alice, bob));

        assertRejectedWith(result, FailureReason.CURRENCY_MISMATCH);
    }

    @Test void insufficientFunds_rejects() {
        var alice = fx.seedLiabilityAccount("alice", "GBP");
        var bob   = fx.seedLiabilityAccount("bob",   "GBP");
        fx.giveOpeningBalance(alice, Money.of("5.00", "GBP"));

        var result = fx.postPayment.execute(
            instruction("tx-1", Money.of("10.00", "GBP"), alice, bob));

        assertRejectedWith(result, FailureReason.INSUFFICIENT_FUNDS);
        assertThat(fx.ledger.balanceOf(alice.id())).isEqualTo(Money.of("5.00", "GBP"));
    }

    @Test void exactBalanceAllowed() {
        var alice = fx.seedLiabilityAccount("alice", "GBP");
        var bob   = fx.seedLiabilityAccount("bob",   "GBP");
        fx.giveOpeningBalance(alice, Money.of("10.00", "GBP"));

        var result = fx.postPayment.execute(
            instruction("tx-1", Money.of("10.00", "GBP"), alice, bob));

        assertThat(result).isInstanceOf(PostPaymentResult.PostedNew.class);
        assertThat(fx.ledger.balanceOf(alice.id())).isEqualTo(Money.zero("GBP"));
    }

    // ── Sequential payments ────────────────────────────────────────────────

    @Test void multipleSequentialPayments_balancesAccumulate() {
        var alice = fx.seedLiabilityAccount("alice", "GBP");
        var bob   = fx.seedLiabilityAccount("bob",   "GBP");
        fx.giveOpeningBalance(alice, Money.of("100.00", "GBP"));

        fx.postPayment.execute(instruction("tx-1", Money.of("30.00", "GBP"), alice, bob));
        fx.postPayment.execute(instruction("tx-2", Money.of("25.00", "GBP"), alice, bob));
        fx.postPayment.execute(instruction("tx-3", Money.of("15.00", "GBP"), alice, bob));

        assertThat(fx.ledger.balanceOf(alice.id())).isEqualTo(Money.of("30.00", "GBP"));
        assertThat(fx.ledger.balanceOf(bob.id())).isEqualTo(Money.of("70.00", "GBP"));
        assertThat(fx.outbox.ofType(PaymentPostedEvent.class)).hasSize(3);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void assertRejectedWith(PostPaymentResult result, FailureReason expected) {
        assertThat(result).isInstanceOf(PostPaymentResult.Rejected.class);
        var rej = (PostPaymentResult.Rejected) result;
        assertThat(rej.reason()).isEqualTo(expected);
    }
}
