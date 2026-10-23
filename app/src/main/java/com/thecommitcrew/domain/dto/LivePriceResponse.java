package com.thecommitcrew.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LivePriceResponse(
    String symbol,
    BigDecimal price,
    String source,
    boolean live,
    Instant updatedAt
) {}
