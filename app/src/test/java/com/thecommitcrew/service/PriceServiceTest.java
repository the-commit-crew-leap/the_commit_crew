package com.thecommitcrew.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.thecommitcrew.domain.exception.PriceNotFoundException;
import com.thecommitcrew.domain.exception.NegativePriceException;
import com.thecommitcrew.domain.exception.LivePriceUnavailableException;
import com.thecommitcrew.domain.dto.IntradayPriceResponse;
import com.thecommitcrew.domain.model.PriceHistory;
import com.thecommitcrew.persistence.mapper.PriceHistoryMapper;

class PriceServiceTest {

    @Mock
    private PriceHistoryMapper priceHistoryMapper;

    @Mock
    private AlpacaMarketDataClient alpacaMarketDataClient;

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

    @Test
    @DisplayName("Returns live Alpaca price with source and timestamp")
    void getCurrentLivePriceReturnsAlpacaPrice() {
        PriceService livePriceService = new PriceService(priceHistoryMapper, alpacaMarketDataClient);
        when(alpacaMarketDataClient.isConfigured()).thenReturn(true);
        when(alpacaMarketDataClient.feed()).thenReturn("iex");
        when(alpacaMarketDataClient.getLatestTrade("AAPL"))
            .thenReturn(new AlpacaMarketDataClient.LatestTrade(
                new BigDecimal("343.39"),
                Instant.parse("2026-10-19T10:00:00Z")
            ));

        var result = livePriceService.getCurrentLivePrice("AAPL");

        assertEquals(new BigDecimal("343.39"), result.price());
        assertEquals("ALPACA_IEX", result.source());
        assertEquals(Instant.parse("2026-10-19T10:00:00Z"), result.updatedAt());
        assertEquals(true, result.live());
        verify(priceHistoryMapper, never()).findLatestPriceBySymbol("AAPL");
    }

    @Test
    @DisplayName("Rejects live price requests when Alpaca is not configured")
    void getCurrentLivePriceRejectsUnconfiguredAlpaca() {
        PriceService livePriceService = new PriceService(priceHistoryMapper, alpacaMarketDataClient);
        when(alpacaMarketDataClient.isConfigured()).thenReturn(false);

        assertThrows(
            LivePriceUnavailableException.class,
            () -> livePriceService.getCurrentLivePrice("AAPL")
        );
        verify(alpacaMarketDataClient, never()).getLatestTrade("AAPL");
        verify(priceHistoryMapper, never()).findLatestPriceBySymbol("AAPL");
    }

    @Test
    @DisplayName("Converts Alpaca failures into live price unavailable errors")
    void getCurrentLivePriceConvertsProviderFailure() {
        PriceService livePriceService = new PriceService(priceHistoryMapper, alpacaMarketDataClient);
        when(alpacaMarketDataClient.isConfigured()).thenReturn(true);
        when(alpacaMarketDataClient.getLatestTrade("AAPL"))
            .thenThrow(new IllegalStateException("provider unavailable"));

        assertThrows(
            LivePriceUnavailableException.class,
            () -> livePriceService.getCurrentLivePrice("AAPL")
        );
    }

    @Test
    @DisplayName("Uses Alpaca price when configured for current price requests")
    void getCurrentPriceUsesLiveAlpacaPriceWhenConfigured() {
        PriceService livePriceService = new PriceService(priceHistoryMapper, alpacaMarketDataClient);
        when(alpacaMarketDataClient.isConfigured()).thenReturn(true);
        when(alpacaMarketDataClient.getLatestTrade("AAPL"))
            .thenReturn(new AlpacaMarketDataClient.LatestTrade(
                new BigDecimal("343.39"),
                Instant.parse("2026-10-19T10:00:00Z")
            ));

        BigDecimal result = livePriceService.getCurrentPrice("AAPL");

        assertEquals(new BigDecimal("343.39"), result);
        verify(priceHistoryMapper, never()).findLatestPriceBySymbol("AAPL");
    }

    @Test
    @DisplayName("Uses the current time when a live trade has no timestamp")
    void getCurrentLivePriceUsesCurrentTimeWhenTimestampIsMissing() {
        PriceService livePriceService = new PriceService(priceHistoryMapper, alpacaMarketDataClient);
        when(alpacaMarketDataClient.isConfigured()).thenReturn(true);
        when(alpacaMarketDataClient.feed()).thenReturn("iex");
        when(alpacaMarketDataClient.getLatestTrade("AAPL"))
            .thenReturn(new AlpacaMarketDataClient.LatestTrade(new BigDecimal("343.39"), null));

        Instant before = Instant.now();
        var result = livePriceService.getCurrentLivePrice("AAPL");
        Instant after = Instant.now();

        assertNotNull(result.updatedAt());
        assertFalse(result.updatedAt().isBefore(before));
        assertFalse(result.updatedAt().isAfter(after));
    }

    @Test
    @DisplayName("Returns intraday prices from configured Alpaca")
    void getIntradayPricesReturnsAlpacaResponse() {
        PriceService livePriceService = new PriceService(priceHistoryMapper, alpacaMarketDataClient);
        IntradayPriceResponse response = new IntradayPriceResponse(
            "AAPL",
            LocalDate.of(2026, 10, 19),
            "ALPACA_IEX",
            true,
            Instant.parse("2026-10-19T19:59:00Z"),
            java.util.List.of()
        );
        when(alpacaMarketDataClient.isConfigured()).thenReturn(true);
        when(alpacaMarketDataClient.getIntradayPrices("AAPL")).thenReturn(response);

        assertEquals(response, livePriceService.getIntradayPrices("AAPL"));
        verify(alpacaMarketDataClient).getIntradayPrices("AAPL");
    }

    @Test
    @DisplayName("Rejects intraday prices when Alpaca is not configured")
    void getIntradayPricesRejectsUnconfiguredAlpaca() {
        PriceService livePriceService = new PriceService(priceHistoryMapper, alpacaMarketDataClient);
        when(alpacaMarketDataClient.isConfigured()).thenReturn(false);

        assertThrows(
            LivePriceUnavailableException.class,
            () -> livePriceService.getIntradayPrices("AAPL")
        );
        verify(alpacaMarketDataClient, never()).getIntradayPrices("AAPL");
    }

    @Test
    @DisplayName("Converts intraday provider failures into unavailable errors")
    void getIntradayPricesConvertsProviderFailure() {
        PriceService livePriceService = new PriceService(priceHistoryMapper, alpacaMarketDataClient);
        when(alpacaMarketDataClient.isConfigured()).thenReturn(true);
        when(alpacaMarketDataClient.getIntradayPrices("AAPL"))
            .thenThrow(new IllegalStateException("provider unavailable"));

        assertThrows(
            LivePriceUnavailableException.class,
            () -> livePriceService.getIntradayPrices("AAPL")
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

            assertThrows(PriceNotFoundException.class, () -> priceService.getCurrentPrice(symbol));
        }

        @Test
        @DisplayName("Throws NegativePriceException when close price is negative")
        void getCurrentPriceThrowsExceptionWhenPriceNegative() {
            when(mockPriceHistory.getClosePrice())
                .thenReturn(new BigDecimal("-10.00"));
            
            when(priceHistoryMapper.findLatestPriceBySymbol(symbol))
                .thenReturn(Optional.of(mockPriceHistory));

            assertThrows(NegativePriceException.class, () -> priceService.getCurrentPrice(symbol));
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