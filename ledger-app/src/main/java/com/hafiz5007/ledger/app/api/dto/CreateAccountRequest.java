package com.hafiz5007.ledger.app.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(min = 3, max = 3) String currency,
    @NotNull AccountTypeDto type
) {
    public enum AccountTypeDto { ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE }
}
