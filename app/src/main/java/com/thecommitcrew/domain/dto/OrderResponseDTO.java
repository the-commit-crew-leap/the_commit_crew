package com.thecommitcrew.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;
import com.thecommitcrew.domain.enums.AssetClass;

public record OrderResponseDTO (

    UUID orderId,
    String accountId,
    String symbol,
    OrderSide side,
    long quantity,
    BigDecimal price,
    BigDecimal executionPrice,
    OrderStatus status,
    LocalDateTime createdOn,
    AssetClass assetClass

) {
    public OrderResponseDTO(
        UUID orderId,
        String accountId,
        String symbol,
        OrderSide side,
        long quantity,
        BigDecimal price,
        OrderStatus status,
        LocalDateTime createdOn
    ) {
        this(orderId, accountId, symbol, side, quantity, price, null, status, createdOn, null);
    }

    public OrderResponseDTO(
        UUID orderId,
        String accountId,
        String symbol,
        OrderSide side,
        long quantity,
        BigDecimal price,
        BigDecimal executionPrice,
        OrderStatus status,
        LocalDateTime createdOn
    ) {
        this(orderId, accountId, symbol, side, quantity, price, executionPrice, status, createdOn, null);
    }

}
