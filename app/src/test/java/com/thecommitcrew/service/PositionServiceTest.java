package com.thecommitcrew.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;
import com.thecommitcrew.domain.exception.NegativePriceException;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.domain.model.Position;

public class PositionServiceTest {

    private PositionService positionService;
    private Position position;
    private Order buyOrder;
    private Order sellOrder;
    private long quantity;
    private long zeroQuantity;
    private BigDecimal averageCost;
    private BigDecimal currentPrice;
    private BigDecimal negativeCurrentPrice;
    private BigDecimal loweredCurrentPrice;

    @BeforeEach
    void setUp() {
        positionService = new PositionService();
        quantity = 10L;
        zeroQuantity = 0L;
        averageCost = new BigDecimal("100");
        currentPrice = new BigDecimal("150");
        negativeCurrentPrice = new BigDecimal("-10");
        loweredCurrentPrice = new BigDecimal("50");
        position = new Position(1L, "TSLA", quantity, averageCost);
        buyOrder = new Order(UUID.randomUUID(), 1L, "TSLA", OrderSide.BUY, 5L, new BigDecimal("110"), OrderStatus.NEW, LocalDateTime.now(), "idempotency-1");
        sellOrder = new Order(UUID.randomUUID(), 1L, "TSLA", OrderSide.SELL, 3L, new BigDecimal("120"), OrderStatus.NEW, LocalDateTime.now(), "idempotency-3");
    }

    @Nested
    @DisplayName("Market value calculations")
    class MarketValueCalculations {
        @Test
        @DisplayName("Calculates correctly with valid inputs")
        void marketValueCalculatesCorrectly() throws NegativePriceException {
            assertEquals(new BigDecimal("1500"), positionService.marketValue(position, currentPrice));
        }

        @Test
        @DisplayName("Doesn't update position when quantity is zero")
        void marketValueWithZeroQuantity() throws NegativePriceException {
            Position positionZero = new Position(1L, "TSLA", zeroQuantity, averageCost);
            assertEquals(new BigDecimal("0"), positionService.marketValue(positionZero, currentPrice));
        }

        @Test
        @DisplayName("Throws exception for negative price")
        void marketValueWithNegativePrice() {
            assertThrows(NegativePriceException.class, () -> {
                positionService.marketValue(position, negativeCurrentPrice);
            });
        }
    }

    @Nested
    @DisplayName("Unrealized profit/loss calculations")
    class UPLOperations {
        @Test
        @DisplayName("Calculates profit correctly with valid inputs")
        void unrealizedProfitCalculatesCorrectly() throws NegativePriceException {
            assertEquals(new BigDecimal("500"), positionService.unrealizedProfitLoss(position, currentPrice));
        }

        @Test
        @DisplayName("Calculates loss correctly with valid inputs")
        void unrealizedLossCalculatesCorrectly() throws NegativePriceException {
            assertEquals(new BigDecimal("-500"), positionService.unrealizedProfitLoss(position, loweredCurrentPrice));
        }

        @Test
        @DisplayName("Throws exception for negative price")
        void unrealizedProfitLossWithNegativePrice() {
            assertThrows(NegativePriceException.class, () -> {
                positionService.unrealizedProfitLoss(position, negativeCurrentPrice);
            });
        }
    }

    @Nested
    @DisplayName("Apply order operations")
    class ApplyOrderOperations {
        @Test
        @DisplayName("Adds to position successfully with valid inputs")
        void applyBuyOrderIncreasesQuantity() {
            Position updatedPosition = positionService.applyOrder(position, buyOrder);
            
            assertEquals(15L, updatedPosition.getQuantity());
        }

        @Test
        @DisplayName("Calculates new average cost when BUY successfully")
        void applyBuyOrderUpdatesAverageCost() {
            Position updatedPosition = positionService.applyOrder(position, buyOrder);
            
            assertEquals(new BigDecimal("103"), updatedPosition.getAverageCost());
        }

        @Test
        @DisplayName("Subtracts from position successfully with valid inputs")
        void applySellOrderDecreasesQuantity() {
            Position updatedPosition = positionService.applyOrder(position, sellOrder);
            
            assertEquals(7L, updatedPosition.getQuantity());
        }

        @Test
        @DisplayName("Doesn't update average cost when SELL")
        void applySellOrderKeepsAverageCost() {
            Position updatedPosition = positionService.applyOrder(position, sellOrder);
            
            assertEquals(new BigDecimal("100"), updatedPosition.getAverageCost());
        }

        @Test
        @DisplayName("Closes position when selling all owned")
        void applySellOrderClosingPosition() {
            Order sellOrderClosing = new Order(UUID.randomUUID(), 1L, "TSLA", OrderSide.SELL, 10L, new BigDecimal("120"), OrderStatus.NEW, LocalDateTime.now(), "idempotency-1");
            Position updatedPosition = positionService.applyOrder(position, sellOrderClosing);
            
            assertEquals(0L, updatedPosition.getQuantity());
            assertEquals(BigDecimal.ZERO, updatedPosition.getAverageCost());
        }

        @Test
        @DisplayName("Throws exception when selling more than owned")
        void applyOrderThrowsExceptionWhenSellingMoreThanOwned() {
            Order sellOrderTooMany = new Order(UUID.randomUUID(), 1L, "TSLA", OrderSide.SELL, 15L, new BigDecimal("120"), OrderStatus.NEW, LocalDateTime.now(), "idempotency-1");
            
            assertThrows(IllegalArgumentException.class, () -> {
                positionService.applyOrder(position, sellOrderTooMany);
            });
        }
    }
}
