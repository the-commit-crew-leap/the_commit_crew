package com.thecommitcrew.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thecommitcrew.persistence.entity.WatchlistEntity;
import com.thecommitcrew.persistence.entity.WatchlistId;

public interface WatchlistRepository extends JpaRepository<WatchlistEntity, WatchlistId> {
    List<WatchlistEntity> findByAccountIdOrderByAddedAtAsc(String accountId);
    Optional<WatchlistEntity> findByAccountIdAndSymbol(String accountId, String symbol);
    void deleteByAccountIdAndSymbol(String accountId, String symbol);
}
