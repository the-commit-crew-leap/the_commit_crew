package com.thecommitcrew.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.thecommitcrew.domain.dto.MarketTickerResponse;

class MarketTickerServiceTest {

    @Mock
    private AlpacaMarketDataClient alpacaMarketDataClient;

    private MarketTickerService marketTickerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        marketTickerService = new MarketTickerService(alpacaMarketDataClient);
    }

    @Test
    void getDefaultMarketsReturnsUnavailableMarketsWhenAlpacaIsNotConfigured() {
        when(alpacaMarketDataClient.isConfigured()).thenReturn(false);

        List<MarketTickerResponse> markets = marketTickerService.getDefaultMarkets();

        assertEquals(MarketTickerService.DEFAULT_SYMBOLS, markets.stream()
            .map(market -> market.symbol())
            .toList());
        assertEquals(4, markets.size());
        assertTrue(markets.stream().allMatch(market ->
            market.price() == null
                && market.changePercent() == null
                && market.source() == null
                && !market.live()
                && market.updatedAt() == null
        ));
        verify(alpacaMarketDataClient, never()).getLatestCryptoSnapshot("BTC/USD");
    }

    @Test
    void getDefaultMarketsMapsSnapshotAndCalculatesChangePercent() {
        Instant updatedAt = Instant.parse("2026-10-20T14:30:00Z");
        when(alpacaMarketDataClient.isConfigured()).thenReturn(true);
        when(alpacaMarketDataClient.getLatestCryptoSnapshot("BTC/USD"))
            .thenReturn(new AlpacaMarketDataClient.CryptoSnapshot(
                new BigDecimal("110"),
                new BigDecimal("100"),
                updatedAt
            ));
        when(alpacaMarketDataClient.getLatestCryptoSnapshot("ETH/USD"))
            .thenReturn(new AlpacaMarketDataClient.CryptoSnapshot(
                new BigDecimal("2000"),
                new BigDecimal("2100"),
                updatedAt
            ));
        when(alpacaMarketDataClient.getLatestCryptoSnapshot("SOL/USD"))
            .thenReturn(new AlpacaMarketDataClient.CryptoSnapshot(
                new BigDecimal("150"),
                null,
                updatedAt
            ));
        when(alpacaMarketDataClient.getLatestCryptoSnapshot("AVAX/USD"))
            .thenReturn(new AlpacaMarketDataClient.CryptoSnapshot(
                new BigDecimal("40"),
                new BigDecimal("40"),
                updatedAt
            ));

        List<MarketTickerResponse> markets = marketTickerService.getDefaultMarkets();

        assertEquals(new BigDecimal("110"), markets.get(0).price());
        assertEquals(0, new BigDecimal("10.00000000").compareTo(markets.get(0).changePercent()));
        assertEquals(0, new BigDecimal("-4.76190500").compareTo(markets.get(1).changePercent()));
        assertNull(markets.get(2).changePercent());
        assertEquals(0, BigDecimal.ZERO.compareTo(markets.get(3).changePercent()));
        assertEquals("ALPACA_CRYPTO", markets.get(0).source());
        assertTrue(markets.get(0).live());
        assertEquals(updatedAt, markets.get(0).updatedAt());
    }

    @Test
    void getDefaultMarketsUsesCurrentTimeWhenSnapshotTimestampIsMissing() {
        Instant before = Instant.now();
        when(alpacaMarketDataClient.isConfigured()).thenReturn(true);
        when(alpacaMarketDataClient.getLatestCryptoSnapshot("BTC/USD"))
            .thenReturn(new AlpacaMarketDataClient.CryptoSnapshot(
                new BigDecimal("100"),
                new BigDecimal("100"),
                null
            ));
        when(alpacaMarketDataClient.getLatestCryptoSnapshot("ETH/USD"))
            .thenThrow(new IllegalStateException("not available"));
        when(alpacaMarketDataClient.getLatestCryptoSnapshot("SOL/USD"))
            .thenThrow(new IllegalStateException("not available"));
        when(alpacaMarketDataClient.getLatestCryptoSnapshot("AVAX/USD"))
            .thenThrow(new IllegalStateException("not available"));

        List<MarketTickerResponse> markets = marketTickerService.getDefaultMarkets();

        Instant after = Instant.now();
        assertNotNull(markets.get(0).updatedAt());
        assertFalse(markets.get(0).updatedAt().isBefore(before));
        assertFalse(markets.get(0).updatedAt().isAfter(after));
        assertUnavailable(markets.get(1));
        assertUnavailable(markets.get(2));
        assertUnavailable(markets.get(3));
    }

    private void assertUnavailable(MarketTickerResponse market) {
        assertNull(market.price());
        assertNull(market.changePercent());
        assertNull(market.source());
        assertFalse(market.live());
        assertNull(market.updatedAt());
    }
}