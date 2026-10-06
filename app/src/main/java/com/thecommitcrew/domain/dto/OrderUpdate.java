package com.thecommitcrew.domain.dto;

import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

import java.util.UUID;

/**
 * A change in an order's lifecycle, pushed to the trader over the
 * {@code /api/accounts/{accountId}/order-updates} Server-Sent Events stream:
 * NEW when accepted, then FILLED (with {@code fillPrice}) or REJECTED.
 */
public record OrderUpdate(
        UUID orderId,
        String accountId,
        String symbol,
        OrderSide side,
        int quantity,
        BigDecimal price,
        OrderStatus status,
        BigDecimal fillPrice,
        String message,
        Instant timestamp
) {
}
