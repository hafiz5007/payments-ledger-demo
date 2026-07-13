package com.hafiz5007.ledger.infrastructure.services;

import com.hafiz5007.ledger.application.PostPaymentResult;
import com.hafiz5007.ledger.application.PostPaymentUseCase;
import com.hafiz5007.ledger.domain.model.PaymentInstruction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring-managed transactional wrapper around the pure Java
 * {@link PostPaymentUseCase}.
 * <p>
 * The Application module is framework-free and cannot annotate its own
 * methods with {@code @Transactional}. This class is the seam: one method,
 * annotated at the class level, that delegates to the use case. Both the
 * Kafka consumer and (in Phase 5) the REST controller call this service —
 * every side effect the use case triggers ends up in the same JPA
 * transaction.
 * <p>
 * The atomicity story: {@link JpaLedgerEntryStore#append} writes entry +
 * postings + balance projection updates; {@link JpaOutboxPublisher#publish}
 * inserts the outbox row. All under one {@code @Transactional} — if any of
 * them throws, the whole transaction rolls back and nothing is persisted.
 * That's the outbox pattern working end-to-end.
 */
@Service
public class PostPaymentService {

    private final PostPaymentUseCase useCase;

    public PostPaymentService(PostPaymentUseCase useCase) {
        this.useCase = useCase;
    }

    @Transactional
    public PostPaymentResult execute(PaymentInstruction instruction) {
        return useCase.execute(instruction);
    }
}
