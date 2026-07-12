package com.hafiz5007.ledger.infrastructure.config;

import com.hafiz5007.ledger.domain.ports.AccountRepository;
import com.hafiz5007.ledger.domain.ports.Clock;
import com.hafiz5007.ledger.domain.ports.IdempotencyStore;
import com.hafiz5007.ledger.domain.ports.LedgerEntryStore;
import com.hafiz5007.ledger.domain.ports.OutboxPublisher;
import com.hafiz5007.ledger.domain.service.DoubleEntryValidator;
import com.hafiz5007.ledger.domain.service.LedgerPoster;
import com.hafiz5007.ledger.infrastructure.adapters.JpaAccountRepository;
import com.hafiz5007.ledger.infrastructure.adapters.JpaIdempotencyStore;
import com.hafiz5007.ledger.infrastructure.adapters.JpaLedgerEntryStore;
import com.hafiz5007.ledger.infrastructure.adapters.JpaOutboxPublisher;
import com.hafiz5007.ledger.infrastructure.adapters.SystemClock;
import com.hafiz5007.ledger.infrastructure.repositories.AccountBalanceJpaRepository;
import com.hafiz5007.ledger.infrastructure.repositories.AccountJpaRepository;
import com.hafiz5007.ledger.infrastructure.repositories.IdempotencyJpaRepository;
import com.hafiz5007.ledger.infrastructure.repositories.LedgerEntryJpaRepository;
import com.hafiz5007.ledger.infrastructure.repositories.OutboxJpaRepository;
import com.hafiz5007.ledger.infrastructure.repositories.PostingJpaRepository;
import com.hafiz5007.ledger.infrastructure.serialization.DomainEventJsonMapper;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Single Spring @Configuration that hands out every domain port. The
 * Phase 5 Spring Boot host imports this via {@code @Import(InfrastructureConfig.class)}
 * — one line of wiring for the whole persistence layer.
 * <p>
 * Kept as an explicit configuration class (not component scanning) so
 * the wiring is auditable in one place. Component scanning across a
 * multi-module setup is a common source of "why did this bean spring up?"
 * confusion.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.hafiz5007.ledger.infrastructure.repositories")
@EntityScan(basePackages = "com.hafiz5007.ledger.infrastructure.entities")
public class InfrastructureConfig {

    @Bean
    public Clock clock() {
        return new SystemClock();
    }

    @Bean
    public DomainEventJsonMapper domainEventJsonMapper() {
        return new DomainEventJsonMapper();
    }

    @Bean
    public AccountRepository accountRepository(
            AccountJpaRepository accounts,
            AccountBalanceJpaRepository balances,
            Clock clock) {
        return new JpaAccountRepository(accounts, balances, clock);
    }

    @Bean
    public LedgerEntryStore ledgerEntryStore(
            LedgerEntryJpaRepository ledgerEntries,
            PostingJpaRepository postings,
            AccountJpaRepository accounts,
            AccountBalanceJpaRepository balances,
            Clock clock) {
        return new JpaLedgerEntryStore(ledgerEntries, postings, accounts, balances, clock);
    }

    @Bean
    public IdempotencyStore idempotencyStore(IdempotencyJpaRepository repository, Clock clock) {
        return new JpaIdempotencyStore(repository, clock);
    }

    @Bean
    public OutboxPublisher outboxPublisher(
            OutboxJpaRepository outbox,
            DomainEventJsonMapper json,
            Clock clock) {
        return new JpaOutboxPublisher(outbox, json, clock);
    }

    // Domain services that need a bean because the Application layer's
    // use cases inject them via their constructors.
    @Bean
    public DoubleEntryValidator doubleEntryValidator() {
        return new DoubleEntryValidator();
    }

    @Bean
    public LedgerPoster ledgerPoster(DoubleEntryValidator validator, Clock clock) {
        return new LedgerPoster(validator, clock);
    }
}
