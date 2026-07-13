package com.hafiz5007.ledger.app.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PostPaymentRequest(
    @NotBlank String paymentId,
    @NotBlank @Size(max = 64) String transactionId,
    @NotBlank String fromAccountId,
    @NotBlank String toAccountId,
    @NotBlank @Pattern(regexp = "^-?\\d+(\\.\\d+)?$", message = "amount must be a decimal string") String amount,
    @NotBlank @Size(min = 3, max = 3) String currency,
    @NotBlank @Size(max = 200) String reference
) { }
