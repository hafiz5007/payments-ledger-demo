package com.hafiz5007.ledger.infrastructure.repositories;

import com.hafiz5007.ledger.infrastructure.entities.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> { }
