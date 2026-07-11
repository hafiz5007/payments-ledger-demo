package com.hafiz5007.ledger.application;

import com.hafiz5007.ledger.domain.model.AccountType;

import java.util.Currency;
import java.util.Objects;

/**
 * Input to {@link CreateAccountUseCase}. Records here so the input shape
 * is verbatim what the caller has to construct; no wrapping in framework
 * types.
 */
public record CreateAccountCommand(String name, AccountType type, Currency currency) {
    public CreateAccountCommand {
        Objects.requireNonNull(name, "name required");
        Objects.requireNonNull(type, "type required");
        Objects.requireNonNull(currency, "currency required");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
    }
}
