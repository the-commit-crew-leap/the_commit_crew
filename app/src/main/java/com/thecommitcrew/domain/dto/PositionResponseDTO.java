package com.thecommitcrew.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.annotation.Nullable;

public record PositionResponseDTO (

    String symbol,
    long quantity,
    BigDecimal averageCost,
    @Nullable BigDecimal currentPrice,
    @Nullable BigDecimal marketValue,
    @Nullable BigDecimal unrealizedPnL,
    @Nullable BigDecimal unrealizedPnLPercent,
    @Nullable String priceSource,
    @Nullable Instant priceUpdatedAt,
    boolean live

) {
    public PositionResponseDTO(
        String symbol,
        long quantity,
        BigDecimal averageCost,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnL,
        BigDecimal unrealizedPnLPercent
    ) {
        this(symbol, quantity, averageCost, currentPrice, marketValue, unrealizedPnL, unrealizedPnLPercent, null, null, false);
    }
}
