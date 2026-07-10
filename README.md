# payments-ledger-demo

A double-entry accounting ledger with Kafka-based payment ingestion, event sourcing, and the outbox pattern for effectively-once downstream delivery. Built in Java 21 + Spring Boot 3 phase-by-phase, with Clean Architecture enforced by Gradle module boundaries.

> A senior backend interviewer will ask you three things about a payments system: how you keep the books balanced, how you handle idempotency, and how you deliver events without double-publishing. This repo answers all three.

**Status: Phase 1 of 5 — Domain module only.**

## Phases

| Phase | Ships | Status |
| --- | --- | --- |
| **1 — Domain** | Java 21 records: `Money` (BigDecimal + currency safety), `Posting`, `LedgerEntry` (balancing invariant in the constructor). Service interfaces (ports). Domain events as a sealed interface hierarchy. Unit tests. Zero framework deps. | **done** |
| **2 — Application** | Use cases (PostPayment, ReplayPayment), idempotency wrapper, in-memory handler tests. | pending |
| **3 — Infrastructure** | Postgres + JPA event store + outbox table + Flyway migration. | pending |
| **4 — Kafka** | Consumer for inbound `PaymentSubmitted`, outbox-relay worker publishing to `PaymentPosted`. | pending |
| **5 — Boot host + REST + Docker + CI** | Runnable Spring Boot app, docker-compose (Postgres + Kafka + app), CI, architecture diagrams. | pending |

Read the git history — each phase is a single commit that stands on its own.

## What Phase 1 gives you

A `ledger-domain` Gradle sub-module with **zero** Spring, JPA, Jackson, or Kafka dependencies. If you type `import org.springframework...` inside this module, the build fails at compile time — those packages aren't on the classpath.

The interesting types:

- **`Money`** — `BigDecimal` + `Currency` value object. Currency-safe arithmetic (adding GBP + USD throws). Scale normalised to the currency's fraction digits so `10` and `10.00` GBP compare equal. Constructor rejects extra precision (`10.001` GBP is illegal). No floating point. Anywhere.
- **`Posting`** — one side of a double-entry, with `AccountId`, `PostingSide` (DEBIT / CREDIT), and a positive `Money` amount. Rejects zero and negative amounts.
- **`LedgerEntry`** — a record that enforces the balancing invariant *in its constructor*. You cannot construct an entry where debits don't equal credits per currency. Every downstream consumer gets to assume the invariant.
- **Sealed `DomainEvent`** hierarchy — `AccountCreatedEvent`, `PaymentPostedEvent`, `PaymentFailedEvent`. Exhaustive `switch` becomes safe.
- **Ports** — `AccountRepository`, `LedgerEntryStore`, `IdempotencyStore`, `OutboxPublisher`, `Clock`. Every side effect from the domain is behind an interface.
- **Services** — `DoubleEntryValidator` for rules beyond balancing, `LedgerPoster` that turns a `PaymentInstruction` into a balanced entry.

Fifteen JUnit 5 tests covering `Money` arithmetic + safety, the `LedgerEntry` balancing invariant (including a multi-currency case), and `LedgerPoster` mapping.

## Build (Phase 1)

```bash
./gradlew :ledger-domain:build
./gradlew :ledger-domain:test
```

The `:ledger-domain:test` task runs the whole suite in under a second because there's no Spring context to bootstrap.

## Why Java 21 (not Kotlin)?

Kotlin covers KYC in the portfolio; Java 21 covers this one. Records + pattern matching + sealed types give Java the same "make illegal states unrepresentable" ergonomics Kotlin has with data classes and sealed classes. Any senior JVM interviewer wants to see both.

Virtual threads land in Phase 5's Spring Boot host — the `PaymentSubmitted` consumer runs one virtual thread per message with structured concurrency for the DB write + outbox write.

## Architecture roadmap

By Phase 5:

- **Ports and adapters** — the domain talks to storage and messaging through interfaces (`LedgerEntryStore`, `OutboxPublisher`) implemented in the infrastructure module. Swap Postgres for CockroachDB or Kafka for Pulsar without touching the domain.
- **Event sourcing** on the ledger side — every state change is a persisted `LedgerEntry`. Account balances are projections. Auditability is free; time-travel is free.
- **Outbox pattern** for downstream delivery — the ledger write and the outbox write share one DB transaction. A separate relay worker (Phase 4) reads outbox rows and publishes to Kafka, then marks them sent. No dual-write inconsistency; at-least-once to Kafka; consumers are idempotent.
- **Effectively-once** payment handling — every inbound instruction carries a client-supplied `TransactionId`. Duplicate submissions return the previous result rather than re-posting. True exactly-once delivery is impossible; effectively-once processing is achievable.

## License

MIT — see [LICENSE](LICENSE).
