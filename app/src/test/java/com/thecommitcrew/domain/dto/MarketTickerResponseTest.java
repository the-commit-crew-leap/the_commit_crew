package com.thecommitcrew.domain.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class MarketTickerResponseTest {

    @Test
    void storesAllMarketTickerFields() {
        Instant updatedAt = Instant.parse("2026-10-20T14:30:00Z");
        MarketTickerResponse response = new MarketTickerResponse(
            "BTC/USD",
            new BigDecimal("68123.45"),
            new BigDecimal("2.35"),
            "ALPACA_CRYPTO",
            true,
            updatedAt
        );

        assertEquals("BTC/USD", response.symbol());
        assertEquals(new BigDecimal("68123.45"), response.price());
        assertEquals(new BigDecimal("2.35"), response.changePercent());
        assertEquals("ALPACA_CRYPTO", response.source());
        assertTrue(response.live());
        assertEquals(updatedAt, response.updatedAt());
    }

    @Test
    void supportsUnavailableMarketValues() {
        MarketTickerResponse response = new MarketTickerResponse(
            "ETH/USD", null, null, null, false, null
        );

        assertEquals("ETH/USD", response.symbol());
        assertNull(response.price());
        assertNull(response.changePercent());
        assertNull(response.source());
        assertFalse(response.live());
        assertNull(response.updatedAt());
    }
}