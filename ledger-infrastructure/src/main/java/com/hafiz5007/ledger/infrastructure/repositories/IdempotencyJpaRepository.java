package com.hafiz5007.ledger.infrastructure.repositories;

import com.hafiz5007.ledger.infrastructure.entities.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyJpaRepository extends JpaRepository<IdempotencyKeyEntity, String> { }
