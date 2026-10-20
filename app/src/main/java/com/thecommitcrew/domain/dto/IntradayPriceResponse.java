package com.thecommitcrew.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record IntradayPriceResponse(
    String symbol,
    LocalDate sessionDate,
    String source,
    boolean live,
    Instant updatedAt,
    List<IntradayBar> bars
) {
    public record IntradayBar(
        Instant timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
    ) {}
}