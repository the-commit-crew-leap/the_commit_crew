package com.thecommitcrew.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.thecommitcrew.persistence.entity.AccountEntity;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
    Optional<AccountEntity> findByAccountId(String accountId);
}
