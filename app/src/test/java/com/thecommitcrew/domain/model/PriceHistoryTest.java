package com.thecommitcrew.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class PriceHistoryTest {

    private UUID id;
    private String symbol;
    private LocalDate priceDate;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private long volume;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        symbol = "AAPL";
        priceDate = LocalDate.now();
        openPrice = new BigDecimal("150.00");
        highPrice = new BigDecimal("155.50");
        lowPrice = new BigDecimal("148.75");
        closePrice = new BigDecimal("153.25");
        volume = 1000000L;
    }

    @Nested
    @DisplayName("Valid construction")
    class ValidConstruction {
        
        @Test
        @DisplayName("Creates successfully with all valid inputs")
        void createsPriceHistorySuccessfully() {
            PriceHistory priceHistory = new PriceHistory(id, symbol, priceDate, openPrice, highPrice, lowPrice, closePrice, volume);
            
            assertNotNull(priceHistory);
            assertEquals(id, priceHistory.getId());
            assertEquals(symbol, priceHistory.getSymbol());
            assertEquals(priceDate, priceHistory.getPriceDate());
            assertEquals(openPrice, priceHistory.getOpenPrice());
            assertEquals(highPrice, priceHistory.getHighPrice());
            assertEquals(lowPrice, priceHistory.getLowPrice());
            assertEquals(closePrice, priceHistory.getClosePrice());
            assertEquals(volume, priceHistory.getVolume());
        }

        @Test
        @DisplayName("Creates with large price values")
        void createsWithLargePrices() {
            BigDecimal largePrice = new BigDecimal("99999.99");
            long largeVolume = Long.MAX_VALUE;
            
            PriceHistory priceHistory = new PriceHistory(id, symbol, priceDate, largePrice, largePrice, largePrice, largePrice, largeVolume);
            
            assertEquals(largePrice, priceHistory.getClosePrice());
            assertEquals(largeVolume, priceHistory.getVolume());
        }

        @Test
        @DisplayName("Creates with small positive price values")
        void createsWithSmallPrices() {
            BigDecimal smallPrice = new BigDecimal("0.01");
            long smallVolume = 1L;
            
            PriceHistory priceHistory = new PriceHistory(id, symbol, priceDate, smallPrice, smallPrice, smallPrice, smallPrice, smallVolume);
            
            assertEquals(smallPrice, priceHistory.getClosePrice());
            assertEquals(smallVolume, priceHistory.getVolume());
        }
    }

    @Nested
    @DisplayName("Null validations")
    class NullValidations {
        
        @Test
        @DisplayName("Throws exception when ID is null")
        void throwsExceptionWhenIdIsNull() {
            assertThrows(IllegalArgumentException.class, () -> {
                new PriceHistory(null, symbol, priceDate, openPrice, highPrice, lowPrice, closePrice, volume);
            });
        }

        @Test
        @DisplayName("Throws exception when symbol is null")
        void throwsExceptionWhenSymbolIsNull() {
            assertThrows(IllegalArgumentException.class, () -> {
                new PriceHistory(id, null, priceDate, openPrice, highPrice, lowPrice, closePrice, volume);
            });
        }

        @Test
        @DisplayName("Throws exception when symbol is blank")
        void throwsExceptionWhenSymbolIsBlank() {
            assertThrows(IllegalArgumentException.class, () -> {
                new PriceHistory(id, "  ", priceDate, openPrice, highPrice, lowPrice, closePrice, volume);
            });
        }

        @Test
        @DisplayName("Throws exception when price date is null")
        void throwsExceptionWhenPriceDateIsNull() {
            assertThrows(IllegalArgumentException.class, () -> {
                new PriceHistory(id, symbol, null, openPrice, highPrice, lowPrice, closePrice, volume);
            });
        }

        @Test
        @DisplayName("Throws exception when open price is null")
        void throwsExceptionWhenOpenPriceIsNull() {
            assertThrows(IllegalArgumentException.class, () -> {
                new PriceHistory(id, symbol, priceDate, null, highPrice, lowPrice, closePrice, volume);
            });
        }

        @Test
        @DisplayName("Throws exception when high price is null")
        void throwsExceptionWhenHighPriceIsNull() {
            assertThrows(IllegalArgumentException.class, () -> {
                new PriceHistory(id, symbol, priceDate, openPrice, null, lowPrice, closePrice, volume);
            });
        }

        @Test
        @DisplayName("Throws exception when low price is null")
        void throwsExceptionWhenLowPriceIsNull() {
            assertThrows(IllegalArgumentException.class, () -> {
                new PriceHistory(id, symbol, priceDate, openPrice, highPrice, null, closePrice, volume);
            });
        }

        @Test
        @DisplayName("Throws exception when close price is null")
        void throwsExceptionWhenClosePriceIsNull() {
            assertThrows(IllegalArgumentException.class, () -> {
                new PriceHistory(id, symbol, priceDate, openPrice, highPrice, lowPrice, null, volume);
            });
        }
    }

    @Nested
    @DisplayName("Negative price validations")
    class NegativePriceValidations {
        
        @Test
        @DisplayName("Throws exception when open price is negative")
        void throwsExceptionWhenOpenPriceNegative() {
            assertThrows(IllegalArgumentException.class, () -> {
                new PriceHistory(id, symbol, priceDate, new BigDecimal("-10.00"), highPrice, lowPrice, closePrice, volume);
            });
        }

        @Test
        @DisplayName("Throws exception when high price is negative")
        void throwsExceptionWhenHighPriceNegative() {
            assertThrows(IllegalArgumentException.class, () -> {
                new PriceHistory(id, symbol, priceDate, openPrice, new BigDecimal("-10.00"), lowPrice, closePrice, volume);
            });
        }

        @Test
        @DisplayName("Throws exception when low price is negative")
        void throwsExceptionWhenLowPriceNegative() {
            assertThrows(IllegalArgumentException.class, () -> {
                new PriceHistory(id, symbol, priceDate, openPrice, highPrice, new BigDecimal("-10.00"), closePrice, volume);
            });
        }

        @Test
        @DisplayName("Throws exception when close price is negative")
        void throwsExceptionWhenClosePriceNegative() {
            assertThrows(IllegalArgumentException.class, () -> {
                new PriceHistory(id, symbol, priceDate, openPrice, highPrice, lowPrice, new BigDecimal("-10.00"), volume);
            });
        }
    }

    @Nested
    @DisplayName("Volume validations")
    class VolumeValidations {
        
        @Test
        @DisplayName("Throws exception when volume is negative")
        void throwsExceptionWhenVolumeNegative() {
            assertThrows(IllegalArgumentException.class, () -> {
                new PriceHistory(id, symbol, priceDate, openPrice, highPrice, lowPrice, closePrice, -1000L);
            });
        }

        @Test
        @DisplayName("Creates successfully with volume of one")
        void createsSuccessfullyWithVolumeOne() {
            PriceHistory priceHistory = new PriceHistory(id, symbol, priceDate, openPrice, highPrice, lowPrice, closePrice, 1L);
            
            assertEquals(1L, priceHistory.getVolume());
        }
    }

    @Nested
    @DisplayName("Getters")
    class Getters {
        
        @Test
        @DisplayName("All getters return correct values")
        void allGettersReturnCorrectValues() {
            PriceHistory priceHistory = new PriceHistory(id, symbol, priceDate, openPrice, highPrice, lowPrice, closePrice, volume);
            
            assertEquals(id, priceHistory.getId());
            assertEquals(symbol, priceHistory.getSymbol());
            assertEquals(priceDate, priceHistory.getPriceDate());
            assertEquals(openPrice, priceHistory.getOpenPrice());
            assertEquals(highPrice, priceHistory.getHighPrice());
            assertEquals(lowPrice, priceHistory.getLowPrice());
            assertEquals(closePrice, priceHistory.getClosePrice());
            assertEquals(volume, priceHistory.getVolume());
        }
    }
}