package com.thecommitcrew.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.thecommitcrew.domain.dto.OrderUpdate;
import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;

class SseOrderUpdateBroadcasterTest {

    private final SseOrderUpdateBroadcaster broadcaster = new SseOrderUpdateBroadcaster();

    @Test
    void implementsOrderUpdates() {
        assertInstanceOf(OrderUpdates.class, broadcaster);
    }

    @Test
    void publishAcceptsOrderUpdateWithoutThrowing() {
        OrderUpdate update = new OrderUpdate(
                UUID.randomUUID(),
                "ACC-1001",
                "AAPL",
                OrderSide.BUY,
                10,
                new BigDecimal("95.00"),
                OrderStatus.FILLED,
                new BigDecimal("100.00"),
                "Order filled at 100.00",
                Instant.parse("2026-01-01T12:00:00Z"));

        assertDoesNotThrow(() -> broadcaster.publish(update));
    }
}
