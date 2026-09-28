package com.thecommitcrew.domain.dto;

import java.math.BigDecimal;

import jakarta.annotation.Nullable;

public record PositionResponseDTO (

    String symbol,
    long quantity,
    BigDecimal averageCost,
    @Nullable BigDecimal currentPrice,
    @Nullable BigDecimal marketValue,
    @Nullable BigDecimal unrealizedPnL

) {}
