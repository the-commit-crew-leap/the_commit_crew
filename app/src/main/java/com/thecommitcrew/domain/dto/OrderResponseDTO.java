package com.thecommitcrew.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;

public record OrderResponseDTO (

    UUID orderId,
    Long accountId,
    String symbol,
    OrderSide side,
    long quantity,
    BigDecimal price,
    OrderStatus status,
    LocalDateTime createdOn

) {}
