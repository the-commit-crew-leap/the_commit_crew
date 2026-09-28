package com.thecommitcrew.persistence.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.thecommitcrew.domain.model.Position;

@Mapper 
public interface PositionMapper {
    Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);
    List<Position> findByAccountId(Long accountId);
    void save(Position position);
}