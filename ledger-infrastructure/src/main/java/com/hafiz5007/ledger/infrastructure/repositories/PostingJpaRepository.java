package com.hafiz5007.ledger.infrastructure.repositories;

import com.hafiz5007.ledger.infrastructure.entities.PostingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostingJpaRepository extends JpaRepository<PostingEntity, Long> {
    List<PostingEntity> findByLedgerEntryId(UUID ledgerEntryId);
}
