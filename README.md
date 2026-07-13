# payments-ledger-demo

A double-entry accounting ledger with Kafka-based payment ingestion, event sourcing, and the outbox pattern for effectively-once downstream delivery. Built in Java 21 + Spring Boot 3, layer-by-layer with Clean Architecture enforced by Gradle module boundaries.

> A senior backend interviewer will ask you three things about a payments system: how you keep the books balanced, how you handle idempotency, and how you deliver events without double-publishing. This repo answers all three.

**Status: all 5 phases complete.**

[![CI](https://github.com/hafiz5007/payments-ledger-demo/actions/workflows/ci.yml/badge.svg)](https://github.com/hafiz5007/payments-ledger-demo/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/JDK-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![Postgres](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Kafka](https://img.shields.io/badge/Kafka-3.7-231F20?logo=apachekafka&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)

## Architecture

```mermaid
flowchart LR
    subgraph Ingest["Inbound"]
        Client[REST client]
        Producer[Upstream producer]
    end

    subgraph App["payments-ledger (Spring Boot)"]
        REST[REST controllers<br/>@Transactional]
        Consumer[Kafka listener<br/>PaymentSubmitted]
        UseCase[PostPaymentUseCase<br/>pure Java]
        Relay[OutboxRelayWorker<br/>@Scheduled]
    end

    subgraph Storage
        DB[(Postgres 16<br/>ledger_entries<br/>postings<br/>account_balances<br/>idempotency_keys<br/>outbox)]
    end

    subgraph Egress["Outbound"]
        Kafka[(Kafka topics<br/>payments.posted<br/>payments.failed<br/>accounts.created)]
        Downstream[Downstream services<br/>notifications, fraud, ...]
    end

    Client -->|POST /api/v1/payments| REST
    Producer -->|payments.submitted| Consumer
    REST --> UseCase
    Consumer --> UseCase
    UseCase --> DB
    DB --> Relay
    Relay --> Kafka
    Kafka --> Downstream
```

## Phases (read the git history)

Each phase is a single commit. Read them in order for the story:

| Phase | Ships |
| --- | --- |
| **1 — Domain** | Java 21 records: `Money`, `Posting`, `LedgerEntry` (balancing invariant in the constructor). Service interfaces (ports). Sealed `DomainEvent` hierarchy. Unit tests. Zero framework deps. |
| **2 — Application** | `PostPaymentUseCase` with idempotency + sufficient-funds check, `CreateAccountUseCase`, `GetAccountBalanceUseCase`. Sealed `PostPaymentResult`. 12 tests over in-memory fakes. |
| **3 — Infrastructure** | Postgres 16 + JPA + Flyway. JPA entities + Spring Data repositories + adapters implementing every Domain port. Balance projection updated in the same transaction as postings via `SELECT ... FOR UPDATE`. Outbox table with JSONB payloads + partial index on unsent rows. Jackson-based event serialiser. Single `@Configuration`. |
| **4 — Kafka** | `PaymentSubmitted` consumer with exponential-backoff retry → DLT. Transactional `PostPaymentService` — one JPA transaction covers entry + postings + balance + outbox write. Outbox relay worker on a fixed cadence: read pending → publish → mark sent. Idempotent producer + `acks=all` + read-committed consumer. |
| **5 — Boot host + REST + Docker + CI** | Spring Boot `LedgerApplication`. REST endpoints under `/api/v1/`. `docker-compose` with Postgres + Kafka (KRaft, no Zookeeper). Full-stack Testcontainers integration test. GitHub Actions CI. Expanded architecture doc. |

See [`docs/architecture.md`](docs/architecture.md) for the sequence diagrams and the "why" for each design choice.

## Run it

### Prerequisites

- Docker + Docker Compose
- (Optional, for local dev) JDK 21 + Gradle 8.10+

### Fastest — everything in containers

```bash
docker compose up --build
# App:      http://localhost:8080
# Postgres: localhost:5432
# Kafka:    localhost:9092
```

### Try the API

**Create two accounts:**

```bash
alice_id=$(curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"name":"alice","type":"LIABILITY","currency":"GBP"}' | jq -r .id)

bob_id=$(curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"name":"bob","type":"LIABILITY","currency":"GBP"}' | jq -r .id)

capital_id=$(curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"name":"capital","type":"EQUITY","currency":"GBP"}' | jq -r .id)
```

**Seed Alice with 100 GBP (posting from equity → liability):**

```bash
curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId":"'$(uuidgen)'",
    "transactionId":"seed-'$(uuidgen)'",
    "fromAccountId":"'$capital_id'",
    "toAccountId":"'$alice_id'",
    "amount":"100.00",
    "currency":"GBP",
    "reference":"opening balance"
  }'
```

**Post a payment from Alice to Bob:**

```bash
tx=$(uuidgen)
curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId":"'$(uuidgen)'",
    "transactionId":"'$tx'",
    "fromAccountId":"'$alice_id'",
    "toAccountId":"'$bob_id'",
    "amount":"30.00",
    "currency":"GBP",
    "reference":"coffee"
  }'

# {"outcome":"PostedNew","ledgerEntryId":"...","reason":null,"detail":null}
```

**Check balances:**

```bash
curl -s http://localhost:8080/api/v1/accounts/$alice_id/balance
# {"accountId":"...","amount":"70.00","currency":"GBP"}
curl -s http://localhost:8080/api/v1/accounts/$bob_id/balance
# {"accountId":"...","amount":"30.00","currency":"GBP"}
```

**Retry the same transaction id → idempotent no-op:**

```bash
curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId":"'$(uuidgen)'",
    "transactionId":"'$tx'",
    "fromAccountId":"'$alice_id'",
    "toAccountId":"'$bob_id'",
    "amount":"30.00",
    "currency":"GBP",
    "reference":"coffee"
  }'

# {"outcome":"AlreadyPosted","ledgerEntryId":"<same as before>","reason":null,"detail":null}
```

Alice's balance is still 70. That's the effectively-once story working.

## Consume the outbound stream

The relay worker publishes to `payments.posted`, `payments.failed`, and `accounts.created`. Watch them with the Kafka CLI:

```bash
docker exec -it ledger-kafka \
  kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic payments.posted --from-beginning
```

## Module layout

```
payments-ledger-demo/
├── ledger-domain/            pure Java 21, no Spring / JPA / Kafka. records + interfaces.
├── ledger-application/       use cases over the domain. testable without infrastructure.
├── ledger-infrastructure/    JPA adapters + Spring Data + Kafka consumer + outbox relay.
└── ledger-app/               Spring Boot host. REST controllers. main().
```

The compiler enforces the dependency direction. `ledger-domain` cannot import Spring — the sub-module has no Spring dep on its classpath. `ledger-application` can't import JPA. Every framework concern lives at the outer edge.

## Test

```bash
./gradlew build
```

Runs:

- **`ledger-domain`** — 18 unit tests over `Money`, `LedgerEntry`, `LedgerPoster`.
- **`ledger-application`** — 12 tests over the use cases using in-memory fakes.
- **`ledger-app`** — a Testcontainers integration test that spins up Postgres 16 + Kafka, boots the full Spring context, exercises the REST API + outbox flow, and asserts idempotency. Slow (~30s cold start), realistic (real Postgres, real Kafka).

Total: 30 unit tests + 3 integration test scenarios.

## Security posture

- No authentication in the demo. A production deploy would put this behind Spring Security's OAuth 2 resource server (see [`auth-reference-dotnet`](https://github.com/hafiz5007/auth-reference-dotnet) for the pattern) or an API gateway.
- **Idempotency at the DB layer** — the `idempotency_keys.transaction_id` primary key catches duplicate submitters at the database, not just in application code. Two concurrent POSTs with the same `transactionId` can't both write.
- **Balance projection under row lock** — `SELECT ... FOR UPDATE` serialises concurrent posters on the same account. No lost-update possible.
- **Outbox pattern** — no dual-write inconsistency between the ledger and the event stream.
- **JSON schema hardening in the DLT flow** — malformed inbound messages route straight to `payments.submitted.dlt` after retry exhaustion; the consumer's error handler classifies `IllegalArgumentException` as non-retryable.

## Roadmap

- FX / multi-currency posting with a domain-level rate resolver
- Debezium change-data-capture off the `outbox` table (replaces the polling relay)
- Compliance audit trail table populated via the outbox
- Real payment rail integration (Faster Payments notification format ingestion)
- Distributed lock on the outbox relay for multi-node deployments

## License

MIT — see [LICENSE](LICENSE).
