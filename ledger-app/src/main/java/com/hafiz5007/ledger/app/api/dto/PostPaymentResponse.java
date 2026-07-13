package com.hafiz5007.ledger.app.api.dto;

/**
 * Wire response for {@code POST /api/v1/payments}. Fields are populated
 * according to the outcome: {@code ledgerEntryId} is present when a new
 * entry was posted or when a duplicate short-circuit returned the earlier
 * entry; {@code reason}/{@code detail} are present when the payment was
 * rejected.
 */
public record PostPaymentResponse(
    String outcome,           // "PostedNew" | "AlreadyPosted" | "Rejected"
    String ledgerEntryId,     // present on PostedNew + AlreadyPosted
    String reason,            // present on Rejected
    String detail             // present on Rejected
) {
    public static PostPaymentResponse postedNew(String entryId) {
        return new PostPaymentResponse("PostedNew", entryId, null, null);
    }
    public static PostPaymentResponse alreadyPosted(String entryId) {
        return new PostPaymentResponse("AlreadyPosted", entryId, null, null);
    }
    public static PostPaymentResponse rejected(String reason, String detail) {
        return new PostPaymentResponse("Rejected", null, reason, detail);
    }
}
