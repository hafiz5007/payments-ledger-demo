package com.hafiz5007.ledger.infrastructure.repositories;

import com.hafiz5007.ledger.infrastructure.entities.AccountBalanceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountBalanceJpaRepository extends JpaRepository<AccountBalanceEntity, UUID> {

    /**
     * PESSIMISTIC_WRITE (SELECT ... FOR UPDATE) so concurrent posters on the
     * same account serialise on the row rather than racing to update it.
     * Alternative: optimistic concurrency + retry, which is fine at low
     * conflict rates but starves under a hot account.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM AccountBalanceEntity b WHERE b.accountId = :accountId")
    Optional<AccountBalanceEntity> findByIdForUpdate(@Param("accountId") UUID accountId);
}
