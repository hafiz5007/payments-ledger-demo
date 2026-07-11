package com.hafiz5007.ledger.application;

import com.hafiz5007.ledger.domain.event.PaymentFailedEvent;
import com.hafiz5007.ledger.domain.event.PaymentPostedEvent;
import com.hafiz5007.ledger.domain.model.Account;
import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.LedgerEntry;
import com.hafiz5007.ledger.domain.model.Money;
import com.hafiz5007.ledger.domain.model.PaymentInstruction;
import com.hafiz5007.ledger.domain.ports.AccountRepository;
import com.hafiz5007.ledger.domain.ports.Clock;
import com.hafiz5007.ledger.domain.ports.IdempotencyStore;
import com.hafiz5007.ledger.domain.ports.LedgerEntryStore;
import com.hafiz5007.ledger.domain.ports.OutboxPublisher;
import com.hafiz5007.ledger.domain.service.LedgerPoster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * The write path for a payment. Every side effect is behind a Domain port
 * so this can be exercised with in-memory fakes in tests, and swapped for
 * a Postgres + Kafka wire-up in Phase 3-4 without touching this class.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li><b>Idempotency short-circuit.</b> If the transaction id has been seen,
 *       return {@link PostPaymentResult.AlreadyPosted} — no writes, no events.</li>
 *   <li><b>Validate accounts.</b> Both from and to must exist and be active
 *       and match the payment's currency.</li>
 *   <li><b>Sufficient funds.</b> The from-account's balance after the debit
 *       must remain non-negative. No overdraft in this demo.</li>
 *   <li><b>Post the entry + record idempotency + publish event.</b> In
 *       Phase 3 the infrastructure implementation of these three ports
 *       shares a Postgres transaction so a mid-flight crash leaves nothing
 *       half-applied.</li>
 * </ol>
 *
 * <p>Rejections publish {@link PaymentFailedEvent} — downstream systems
 * often care about failures (notifications, retry logic, fraud triage).</p>
 */
public final class PostPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(PostPaymentUseCase.class);

    private final AccountRepository accounts;
    private final LedgerEntryStore ledger;
    private final IdempotencyStore idempotency;
    private final OutboxPublisher outbox;
    private final LedgerPoster poster;
    private final Clock clock;

    public PostPaymentUseCase(
        AccountRepository accounts,
        LedgerEntryStore ledger,
        IdempotencyStore idempotency,
        OutboxPublisher outbox,
        LedgerPoster poster,
        Clock clock
    ) {
        this.accounts    = Objects.requireNonNull(accounts);
        this.ledger      = Objects.requireNonNull(ledger);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.outbox      = Objects.requireNonNull(outbox);
        this.poster      = Objects.requireNonNull(poster);
        this.clock       = Objects.requireNonNull(clock);
    }

    public PostPaymentResult execute(PaymentInstruction instruction) {
        // 1. Idempotency.
        Optional<com.hafiz5007.ledger.domain.model.LedgerEntryId> existing =
            idempotency.findExistingEntry(instruction.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate submission for tx={} — returning existing entry {}",
                instruction.idempotencyKey(), existing.get());
            return new PostPaymentResult.AlreadyPosted(existing.get());
        }

        // 2. Accounts.
        var fromLookup = accounts.findById(instruction.fromAccountId());
        if (fromLookup.isEmpty()) {
            return reject(instruction, PaymentFailedEvent.FailureReason.ACCOUNT_NOT_FOUND,
                "from account " + instruction.fromAccountId() + " not found");
        }
        var from = fromLookup.get();
        if (!from.active()) {
            return reject(instruction, PaymentFailedEvent.FailureReason.ACCOUNT_INACTIVE,
                "from account " + from.id() + " is inactive");
        }

        var toLookup = accounts.findById(instruction.toAccountId());
        if (toLookup.isEmpty()) {
            return reject(instruction, PaymentFailedEvent.FailureReason.ACCOUNT_NOT_FOUND,
                "to account " + instruction.toAccountId() + " not found");
        }
        var to = toLookup.get();
        if (!to.active()) {
            return reject(instruction, PaymentFailedEvent.FailureReason.ACCOUNT_INACTIVE,
                "to account " + to.id() + " is inactive");
        }

        // 3. Currency alignment.
        if (!from.currency().equals(instruction.amount().currency())
            || !to.currency().equals(instruction.amount().currency())) {
            return reject(instruction, PaymentFailedEvent.FailureReason.CURRENCY_MISMATCH,
                "currency mismatch: payment=" + instruction.amount().currency()
                    + " from=" + from.currency() + " to=" + to.currency());
        }

        // 4. Sufficient funds — no overdraft in this demo.
        if (!hasSufficientFunds(from, instruction.amount())) {
            return reject(instruction, PaymentFailedEvent.FailureReason.INSUFFICIENT_FUNDS,
                "insufficient funds on account " + from.id());
        }

        // 5. Post.
        LedgerEntry entry = poster.post(instruction);
        ledger.append(entry);
        idempotency.record(instruction.idempotencyKey(), entry.id());
        outbox.publish(new PaymentPostedEvent(
            instruction.paymentId(),
            instruction.idempotencyKey(),
            entry.id(),
            instruction.amount(),
            clock.nowUtc()
        ));

        log.info("Posted payment {} tx={} entry={}",
            instruction.paymentId(), instruction.idempotencyKey(), entry.id());

        return new PostPaymentResult.PostedNew(entry.id(), instruction.amount());
    }

    /**
     * True when a debit of {@code amount} on {@code from} leaves the balance
     * non-negative. For an ASSET or LIABILITY account, "sufficient funds"
     * means the same thing from the owner's perspective — do you have
     * enough value in the account? The {@link LedgerEntryStore#balanceOf}
     * contract already returns the owner-perspective balance.
     */
    private boolean hasSufficientFunds(Account from, Money debitAmount) {
        Money current = ledger.balanceOf(from.id());
        return !current.minus(debitAmount).isNegative();
    }

    private PostPaymentResult.Rejected reject(
        PaymentInstruction instruction,
        PaymentFailedEvent.FailureReason reason,
        String detail
    ) {
        log.info("Rejected payment {} tx={} — {}: {}",
            instruction.paymentId(), instruction.idempotencyKey(), reason, detail);
        outbox.publish(new PaymentFailedEvent(
            instruction.paymentId(),
            instruction.idempotencyKey(),
            reason,
            detail,
            clock.nowUtc()
        ));
        return new PostPaymentResult.Rejected(reason, detail);
    }

    /**
     * Convenience type re-export so callers don't need to import from the
     * Domain event package when pattern-matching on the returned reason.
     */
    public static final class Reason {
        private Reason() { }
        public static PaymentFailedEvent.FailureReason accountNotFound()  { return PaymentFailedEvent.FailureReason.ACCOUNT_NOT_FOUND; }
        public static PaymentFailedEvent.FailureReason accountInactive()  { return PaymentFailedEvent.FailureReason.ACCOUNT_INACTIVE; }
        public static PaymentFailedEvent.FailureReason currencyMismatch() { return PaymentFailedEvent.FailureReason.CURRENCY_MISMATCH; }
        public static PaymentFailedEvent.FailureReason insufficientFunds() { return PaymentFailedEvent.FailureReason.INSUFFICIENT_FUNDS; }
        public static PaymentFailedEvent.FailureReason duplicate()        { return PaymentFailedEvent.FailureReason.DUPLICATE_TRANSACTION; }
    }
}
