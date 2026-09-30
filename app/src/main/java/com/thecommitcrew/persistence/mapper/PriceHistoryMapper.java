package com.thecommitcrew.persistence.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import com.thecommitcrew.domain.model.PriceHistory;

@Mapper
public interface PriceHistoryMapper {
    Optional<PriceHistory> findLatestPriceBySymbol(String symbol);
}
