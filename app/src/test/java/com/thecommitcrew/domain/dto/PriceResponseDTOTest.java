package com.thecommitcrew.domain.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PriceResponseDTOTest {

    @Test
    void storesSymbolAndPrice() {
        PriceResponseDTO response = new PriceResponseDTO("AAPL", new BigDecimal("195.50"));

        assertEquals("AAPL", response.symbol());
        assertEquals(new BigDecimal("195.50"), response.price());
    }

    @Test
    void supportsNullPrice() {
        PriceResponseDTO response = new PriceResponseDTO("AAPL", null);

        assertEquals("AAPL", response.symbol());
        assertEquals(null, response.price());
    }
}