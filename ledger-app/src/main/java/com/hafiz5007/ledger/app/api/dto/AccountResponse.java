package com.hafiz5007.ledger.app.api.dto;

import com.hafiz5007.ledger.domain.model.Account;

import java.time.Instant;

public record AccountResponse(
    String id,
    String name,
    String type,
    String currency,
    boolean active,
    Instant createdAtUtc
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(
            a.id().toString(),
            a.name(),
            a.type().name(),
            a.currency().getCurrencyCode(),
            a.active(),
            a.createdAtUtc()
        );
    }
}
