package com.hafiz5007.ledger.application.fakes;

import com.hafiz5007.ledger.application.CreateAccountCommand;
import com.hafiz5007.ledger.application.CreateAccountUseCase;
import com.hafiz5007.ledger.application.GetAccountBalanceUseCase;
import com.hafiz5007.ledger.application.PostPaymentUseCase;
import com.hafiz5007.ledger.domain.model.Account;
import com.hafiz5007.ledger.domain.model.AccountType;
import com.hafiz5007.ledger.domain.service.DoubleEntryValidator;
import com.hafiz5007.ledger.domain.service.LedgerPoster;

import java.time.Instant;
import java.util.Currency;

/**
 * Wires up every port + use case with in-memory fakes. Tests just ask for
 * the use case they want and get a fresh graph — no shared state between
 * test methods.
 */
public final class LedgerTestFixture {

    public final FixedClock clock;
    public final InMemoryAccountRepository accounts;
    public final InMemoryLedgerEntryStore ledger;
    public final InMemoryIdempotencyStore idempotency;
    public final CapturingOutboxPublisher outbox;
    public final LedgerPoster poster;

    public final CreateAccountUseCase createAccount;
    public final PostPaymentUseCase postPayment;
    public final GetAccountBalanceUseCase getBalance;

    public LedgerTestFixture() {
        this(Instant.parse("2026-07-08T12:00:00Z"));
    }

    public LedgerTestFixture(Instant startAt) {
        this.clock       = new FixedClock(startAt);
        this.accounts    = new InMemoryAccountRepository();
        this.ledger      = new InMemoryLedgerEntryStore(accounts);
        this.idempotency = new InMemoryIdempotencyStore();
        this.outbox      = new CapturingOutboxPublisher();
        this.poster      = new LedgerPoster(new DoubleEntryValidator(), clock);

        this.createAccount = new CreateAccountUseCase(accounts, outbox, clock);
        this.postPayment   = new PostPaymentUseCase(accounts, ledger, idempotency, outbox, poster, clock);
        this.getBalance    = new GetAccountBalanceUseCase(accounts, ledger);
    }

    /** Convenience: create an empty liability (customer-deposit) account. */
    public Account seedLiabilityAccount(String name, String currencyCode) {
        return createAccount.execute(new CreateAccountCommand(
            name, AccountType.LIABILITY, Currency.getInstance(currencyCode)
        ));
    }

    /**
     * Convenience: give a customer account an opening balance. Bypasses the
     * use case — writes an opening-balance entry directly against a fresh
     * "capital" equity account so books stay balanced.
     */
    public void giveOpeningBalance(Account account, com.hafiz5007.ledger.domain.model.Money amount) {
        var capital = createAccount.execute(new CreateAccountCommand(
            "capital-" + java.util.UUID.randomUUID(),
            AccountType.EQUITY,
            amount.currency()
        ));
        var openingEntry = new com.hafiz5007.ledger.domain.model.LedgerEntry(
            com.hafiz5007.ledger.domain.model.LedgerEntryId.newId(),
            new com.hafiz5007.ledger.domain.model.TransactionId("seed-" + java.util.UUID.randomUUID()),
            java.util.List.of(
                com.hafiz5007.ledger.domain.model.Posting.debit(capital.id(), amount),
                com.hafiz5007.ledger.domain.model.Posting.credit(account.id(), amount)
            ),
            clock.nowUtc(),
            "opening balance"
        );
        ledger.append(openingEntry);
    }
}
