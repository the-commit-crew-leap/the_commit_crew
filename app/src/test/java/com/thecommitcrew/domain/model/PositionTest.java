package com.thecommitcrew.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class PositionTest {
    
    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {
        @Test
        @DisplayName("Throws exception when account ID is null")
        void throwsExceptionWhenAccountIdIsNull() {
            assertThrows(IllegalArgumentException.class, () -> {
                new Position(null, "AAPL", 10L, new BigDecimal("100"));
            });
        }

        @Test
        @DisplayName("Throws exception when symbol is null")
        void throwsExceptionWhenSymbolIsNull() {
            assertThrows(IllegalArgumentException.class, () -> {
                new Position(1L, null, 10L, new BigDecimal("100"));
            });
        }

        @Test
        @DisplayName("Throws exception when symbol is blank")
        void throwsExceptionWhenSymbolIsBlank() {
            assertThrows(IllegalArgumentException.class, () -> {
                new Position(1L, "   ", 10L, new BigDecimal("100"));
            });
        }

        @Test
        @DisplayName("Throws exception when quantity is negative")
        void throwsExceptionWhenQuantityIsNegative() {
            assertThrows(IllegalArgumentException.class, () -> {
                new Position(1L, "AAPL", -5L, new BigDecimal("100"));
            });
        }

        @Test
        @DisplayName("Throws exception when average cost is null")
        void throwsExceptionWhenAverageCostIsNull() {
            assertThrows(IllegalArgumentException.class, () -> {
                new Position(1L, "AAPL", 10L, null);
            });
        }

        @Test
        @DisplayName("Throws exception when average cost is negative")
        void throwsExceptionWhenAverageCostIsNegative() {
            assertThrows(IllegalArgumentException.class, () -> {
                new Position(1L, "AAPL", 10L, new BigDecimal("-50"));
            });
        }

        @Test
        @DisplayName("Succeeds with valid inputs")
        void succeedsWithValidInputs() {
            Position position = new Position(1L, "AAPL", 10L, new BigDecimal("100"));
            assertNotNull(position);
            assertEquals(1L, position.getAccountId());
        }
    }
}
