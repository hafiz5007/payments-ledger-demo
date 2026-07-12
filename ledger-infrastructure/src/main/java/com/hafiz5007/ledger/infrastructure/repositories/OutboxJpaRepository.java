package com.hafiz5007.ledger.infrastructure.repositories;

import com.hafiz5007.ledger.infrastructure.entities.OutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {

    /**
     * Unsent rows in insertion order — Phase 4's relay worker uses this.
     * Order matters because Kafka partitioning + strict ordering per
     * aggregate need us to publish older events first.
     */
    @Query("SELECT o FROM OutboxEntity o WHERE o.sentAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboxEntity> findUnsent(org.springframework.data.domain.Pageable pageable);
}
