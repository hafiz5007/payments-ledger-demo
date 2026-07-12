package com.hafiz5007.ledger.infrastructure.repositories;

import com.hafiz5007.ledger.infrastructure.entities.LedgerEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryEntity, UUID> {

    /**
     * Newest-first ledger entries touching a specific account, capped.
     * Uses the ({@code postings.account_id}, {@code ledger_entry_id}) index.
     */
    @Query("""
        SELECT DISTINCT le
        FROM LedgerEntryEntity le, PostingEntity p
        WHERE p.ledgerEntryId = le.id
          AND p.accountId = :accountId
        ORDER BY le.occurredAtUtc DESC
    """)
    List<LedgerEntryEntity> findByAccountId(@Param("accountId") UUID accountId,
                                            org.springframework.data.domain.Pageable pageable);
}
