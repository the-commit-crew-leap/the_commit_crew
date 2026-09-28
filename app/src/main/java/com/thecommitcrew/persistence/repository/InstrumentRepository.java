package com.thecommitcrew.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.thecommitcrew.persistence.entity.InstrumentEntity;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, String> {
    Optional<InstrumentEntity> findBySymbol(String symbol);
}