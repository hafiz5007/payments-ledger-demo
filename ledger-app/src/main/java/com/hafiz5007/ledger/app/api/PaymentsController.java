package com.hafiz5007.ledger.app.api;

import com.hafiz5007.ledger.app.api.dto.PostPaymentRequest;
import com.hafiz5007.ledger.app.api.dto.PostPaymentResponse;
import com.hafiz5007.ledger.application.PostPaymentResult;
import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.Money;
import com.hafiz5007.ledger.domain.model.PaymentId;
import com.hafiz5007.ledger.domain.model.PaymentInstruction;
import com.hafiz5007.ledger.domain.model.TransactionId;
import com.hafiz5007.ledger.infrastructure.services.PostPaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Payment intake. Delegates to {@link PostPaymentService} which carries the
 * {@code @Transactional} boundary — the DB write and the outbox insert both
 * happen in one JPA transaction.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentsController {

    private final PostPaymentService service;

    public PaymentsController(PostPaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PostPaymentResponse> post(@Valid @RequestBody PostPaymentRequest request) {
        var instruction = new PaymentInstruction(
            new PaymentId(UUID.fromString(request.paymentId())),
            new TransactionId(request.transactionId()),
            new AccountId(UUID.fromString(request.fromAccountId())),
            new AccountId(UUID.fromString(request.toAccountId())),
            Money.of(request.amount(), request.currency()),
            request.reference()
        );

        var result = service.execute(instruction);

        // switch expression on the sealed PostPaymentResult — exhaustive at compile time.
        return switch (result) {
            case PostPaymentResult.PostedNew p ->
                ResponseEntity.status(201).body(PostPaymentResponse.postedNew(p.ledgerEntryId().toString()));

            case PostPaymentResult.AlreadyPosted p ->
                // 200 with the previous entry id — the caller can still act on the outcome.
                ResponseEntity.ok(PostPaymentResponse.alreadyPosted(p.ledgerEntryId().toString()));

            case PostPaymentResult.Rejected r ->
                // 422 Unprocessable Entity — the request was well-formed but violated
                // a domain rule (unknown account, currency mismatch, insufficient funds).
                ResponseEntity.unprocessableEntity()
                    .body(PostPaymentResponse.rejected(r.reason().name(), r.detail()));
        };
    }
}
