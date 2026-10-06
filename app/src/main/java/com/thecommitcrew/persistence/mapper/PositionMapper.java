package com.thecommitcrew.persistence.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.thecommitcrew.domain.model.Position;

@Mapper 
public interface PositionMapper {
    Optional<Position> findByAccountIdAndSymbol(String accountId, String symbol);
    List<Position> findByAccountId(String accountId);
    void save(Position position);
}