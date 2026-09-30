package com.thecommitcrew.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.thecommitcrew.domain.exception.PriceNotFoundException;
import com.thecommitcrew.domain.exception.NegativePriceException;
import com.thecommitcrew.domain.model.PriceHistory;
import com.thecommitcrew.persistence.mapper.PriceHistoryMapper;

public class PriceServiceTest {

    @Mock
    private PriceHistoryMapper priceHistoryMapper;

    @Mock
    private PriceHistory mockPriceHistory;

    private PriceService priceService;
    private PriceHistory priceHistory;
    private String symbol;
    private BigDecimal closePrice;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        priceService = new PriceService(priceHistoryMapper);
        
        symbol = "AAPL";
        closePrice = new BigDecimal("195.50");
        priceHistory = new PriceHistory(
            UUID.randomUUID(),
            symbol,
            LocalDate.now(),
            new BigDecimal("190.00"),
            new BigDecimal("198.00"),
            new BigDecimal("188.50"),
            closePrice,
            1000000L
        );
    }

    @Nested
    @DisplayName("Get current price")
    class GetCurrentPrice {
        
        @Test
        @DisplayName("Returns close price when price history exists")
        void getCurrentPriceReturnsClosePriceSuccessfully() {
            when(priceHistoryMapper.findLatestPriceBySymbol(symbol))
                .thenReturn(Optional.of(priceHistory));

            BigDecimal result = priceService.getCurrentPrice(symbol);

            assertEquals(closePrice, result);
        }

        @Test
        @DisplayName("Throws PriceNotFoundException when no price found")
        void getCurrentPriceThrowsExceptionWhenNotFound() {
            when(priceHistoryMapper.findLatestPriceBySymbol(symbol))
                .thenReturn(Optional.empty());

            assertThrows(PriceNotFoundException.class, () -> {
                priceService.getCurrentPrice(symbol);
            });
        }

        @Test
        @DisplayName("Throws NegativePriceException when close price is negative")
        void getCurrentPriceThrowsExceptionWhenPriceNegative() {
            when(mockPriceHistory.getClosePrice())
                .thenReturn(new BigDecimal("-10.00"));
            
            when(priceHistoryMapper.findLatestPriceBySymbol(symbol))
                .thenReturn(Optional.of(mockPriceHistory));

            assertThrows(NegativePriceException.class, () -> {
                priceService.getCurrentPrice(symbol);
            });
        }

        @Test
        @DisplayName("Returns zero price when close price is zero")
        void getCurrentPriceReturnsZeroPrice() {
            when(mockPriceHistory.getClosePrice())
                .thenReturn(new BigDecimal("0.00"));
            
            when(priceHistoryMapper.findLatestPriceBySymbol(symbol))
                .thenReturn(Optional.of(mockPriceHistory));

            BigDecimal result = priceService.getCurrentPrice(symbol);

            assertEquals(new BigDecimal("0.00"), result);
        }
    }
}