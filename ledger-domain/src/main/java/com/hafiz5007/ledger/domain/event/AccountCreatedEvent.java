package com.hafiz5007.ledger.domain.event;

import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.AccountType;

import java.time.Instant;
import java.util.Currency;

public record AccountCreatedEvent(
    AccountId accountId,
    String name,
    AccountType type,
    Currency currency,
    Instant occurredAtUtc
) implements DomainEvent { }
