package com.thecommitcrew.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketTickerResponse(
    String symbol,
    BigDecimal price,
    BigDecimal changePercent,
    String source,
    boolean live,
    Instant updatedAt
) {}
