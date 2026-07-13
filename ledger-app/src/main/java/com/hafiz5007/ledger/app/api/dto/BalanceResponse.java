package com.hafiz5007.ledger.app.api.dto;

import com.hafiz5007.ledger.application.GetAccountBalanceUseCase;

public record BalanceResponse(
    String accountId,
    String amount,
    String currency
) {
    public static BalanceResponse from(GetAccountBalanceUseCase.AccountBalance b) {
        return new BalanceResponse(
            b.account().id().toString(),
            b.balance().amount().toPlainString(),
            b.balance().currency().getCurrencyCode()
        );
    }
}
