package com.thecommitcrew.domain.dto;

import java.math.BigDecimal;

public record PriceResponseDTO(
    String symbol,
    BigDecimal price
) {}
