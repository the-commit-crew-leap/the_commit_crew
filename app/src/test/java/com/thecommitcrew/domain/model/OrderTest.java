package com.thecommitcrew.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;

public class OrderTest {

    private UUID validId;
    private Long validAccountId;
    private String validSymbol;
    private OrderSide validSide;
    private long validQuantity;
    private BigDecimal validPrice;
    private OrderStatus validStatus;
    private LocalDateTime validCreatedOn;
    private String validIdempotencyKey;
    private Order order;

    @BeforeEach
    void setUp() {
        validId = UUID.randomUUID();
        validAccountId = 123L;
        validSymbol = "AAPL";
        validSide = OrderSide.BUY;
        validQuantity = 100L;
        validPrice = new BigDecimal("182.45");
        validStatus = OrderStatus.NEW;
        validCreatedOn = LocalDateTime.now();
        validIdempotencyKey = "idem-001";
        order = new Order(
            validId,
            validAccountId,
            validSymbol,
            validSide,
            validQuantity,
            validPrice,
            validStatus,
            validCreatedOn,
            validIdempotencyKey
        );
    }

    @Nested
    @DisplayName("Tests for constructor failures")
    class ConstructorValidation {

        @Test
        @DisplayName("Should throw when Order ID is null")
        void shouldThrowWhenOrderIdIsNull() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                null, validAccountId, validSymbol, validSide,
                validQuantity, validPrice, validStatus, validCreatedOn, validIdempotencyKey
            ));
        }

        @Test
        @DisplayName("Should throw when Account ID is null")
        void shouldThrowWhenAccountIdIsNull() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                validId, null, validSymbol, validSide,
                validQuantity, validPrice, validStatus, validCreatedOn, validIdempotencyKey
            ));
        }

        @Test
        @DisplayName("Should throw when symbol is null")
        void shouldThrowWhenSymbolIsNull() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                validId, validAccountId, null, validSide,
                validQuantity, validPrice, validStatus, validCreatedOn, validIdempotencyKey
            ));
        }

        @Test
        @DisplayName("Should throw when symbol is blank")
        void shouldThrowWhenSymbolIsBlank() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                validId, validAccountId, "   ", validSide,
                validQuantity, validPrice, validStatus, validCreatedOn, validIdempotencyKey
            ));
        }

        @Test
        @DisplayName("Should throw when Order side is null")
        void shouldThrowWhenOrderSideIsNull() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                validId, validAccountId, validSymbol, null,
                validQuantity, validPrice, validStatus, validCreatedOn, validIdempotencyKey
            ));
        }

        @Test
        @DisplayName("Should throw when quantity is zero")
        void shouldThrowWhenQuantityIsZero() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                validId, validAccountId, validSymbol, validSide,
                0L, validPrice, validStatus, validCreatedOn, validIdempotencyKey
            ));
        }

        @Test
        @DisplayName("Should throw when quantity is negative")
        void shouldThrowWhenQuantityIsNegative() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                validId, validAccountId, validSymbol, validSide,
                -100L, validPrice, validStatus, validCreatedOn, validIdempotencyKey
            ));
        }

        @Test
        @DisplayName("Should throw when price is null")
        void shouldThrowWhenPriceIsNull() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                validId, validAccountId, validSymbol, validSide,
                validQuantity, null, validStatus, validCreatedOn, validIdempotencyKey
            ));
        }

        @Test
        @DisplayName("Should throw when price is zero")
        void shouldThrowWhenPriceIsZero() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                validId, validAccountId, validSymbol, validSide,
                validQuantity, BigDecimal.ZERO, validStatus, validCreatedOn, validIdempotencyKey
            ));
        }

        @Test
        @DisplayName("Should throw when price is negative")
        void shouldThrowWhenPriceIsNegative() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                validId, validAccountId, validSymbol, validSide,
                validQuantity, new BigDecimal("-50.00"), validStatus, validCreatedOn, validIdempotencyKey
            ));
        }

        @Test
        @DisplayName("Should throw when status is null")
        void shouldThrowWhenStatusIsNull() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                validId, validAccountId, validSymbol, validSide,
                validQuantity, validPrice, null, validCreatedOn, validIdempotencyKey
            ));
        }

        @Test
        @DisplayName("Should throw when created on is null")
        void shouldThrowWhenCreatedOnIsNull() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                validId, validAccountId, validSymbol, validSide,
                validQuantity, validPrice, validStatus, null, validIdempotencyKey
            ));
        }

        @Test
        @DisplayName("Should throw when idempotency key is null")
        void shouldThrowWhenIdempotencyKeyIsNull() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                validId, validAccountId, validSymbol, validSide,
                validQuantity, validPrice, validStatus, validCreatedOn, null
            ));
        }

        @Test
        @DisplayName("Should throw when idempotency key is blank")
        void shouldThrowWhenIdempotencyKeyIsBlank() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                validId, validAccountId, validSymbol, validSide,
                validQuantity, validPrice, validStatus, validCreatedOn, "   "
            ));
        }
    }

    @Nested
    @DisplayName("Tests for getter methods")
    class GetterMethods {

        @Test
        @DisplayName("Should return correct ID")
        void testGetId() {
            assertEquals(validId, order.getId());
        }

        @Test
        @DisplayName("Should return correct Account ID")
        void testGetAccountId() {
            assertEquals(validAccountId, order.getAccountId());
        }

        @Test
        @DisplayName("Should return correct symbol")
        void testGetSymbol() {
            assertEquals(validSymbol, order.getSymbol());
        }

        @Test
        @DisplayName("Should return correct Order side")
        void testGetSide() {
            assertEquals(validSide, order.getSide());
        }

        @Test
        @DisplayName("Should return correct quantity")
        void testGetQuantity() {
            assertEquals(validQuantity, order.getQuantity());
        }

        @Test
        @DisplayName("Should return correct price")
        void testGetPrice() {
            assertEquals(validPrice, order.getPrice());
        }

        @Test
        @DisplayName("Should return correct status")
        void testGetStatus() {
            assertEquals(validStatus, order.getStatus());
        }

        @Test
        @DisplayName("Should return correct created on")
        void testGetCreatedOn() {
            assertEquals(validCreatedOn, order.getCreatedOn());
        }

        @Test
        @DisplayName("Should return correct idempotency key")
        void testGetIdempotencyKey() {
            assertEquals(validIdempotencyKey, order.getIdempotencyKey());
        }
    }

    @Nested
    @DisplayName("Tests for setter methods")
    class SetterMethods {

        @Test
        @DisplayName("Should set status to FILLED")
        void shouldSetStatusToFilled() {
            order.setStatus(OrderStatus.FILLED);
            assertEquals(OrderStatus.FILLED, order.getStatus());
        }

        @Test
        @DisplayName("Should set status to REJECTED")
        void shouldSetStatusToRejected() {
            order.setStatus(OrderStatus.REJECTED);
            assertEquals(OrderStatus.REJECTED, order.getStatus());
        }

        @Test
        @DisplayName("Should set status to CANCELLED")
        void shouldSetStatusToCancelled() {
            order.setStatus(OrderStatus.CANCELLED);
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
        }
    }
}
