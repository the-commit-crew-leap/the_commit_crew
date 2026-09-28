package com.thecommitcrew.domain.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

public class PositionResponseDTOTest {
    
    @Test
    void testPositionResponseCreation() {
        String symbol = "AAPL";
        long quantity = 100L;
        BigDecimal averageCost = new BigDecimal("150.00");
        BigDecimal currentPrice = new BigDecimal("155.50");
        BigDecimal marketValue = new BigDecimal("15550.00");
        BigDecimal unrealizedPnL = new BigDecimal("550.00");
        
        PositionResponseDTO response = new PositionResponseDTO(
            symbol,
            quantity,
            averageCost,
            currentPrice,
            marketValue,
            unrealizedPnL
        );
        
        assertNotNull(response);
        assertEquals(symbol, response.symbol());
        assertEquals(quantity, response.quantity());
    }
    
    @Test
    void testPositionResponseFields() {
        String symbol = "MSFT";
        long quantity = 50L;
        BigDecimal averageCost = new BigDecimal("300.00");
        BigDecimal currentPrice = new BigDecimal("310.00");
        BigDecimal marketValue = new BigDecimal("15500.00");
        BigDecimal unrealizedPnL = new BigDecimal("500.00");
        
        PositionResponseDTO response = new PositionResponseDTO(
            symbol,
            quantity,
            averageCost,
            currentPrice,
            marketValue,
            unrealizedPnL
        );
        
        assertEquals(symbol, response.symbol());
        assertEquals(quantity, response.quantity());
        assertEquals(averageCost, response.averageCost());
        assertEquals(currentPrice, response.currentPrice());
        assertEquals(marketValue, response.marketValue());
        assertEquals(unrealizedPnL, response.unrealizedPnL());
    }
    
    @Test
    void testPositionResponseWithNullableFields() {
        String symbol = "GOOG";
        long quantity = 10L;
        BigDecimal averageCost = new BigDecimal("2800.00");
        
        PositionResponseDTO response = new PositionResponseDTO(
            symbol,
            quantity,
            averageCost,
            null,
            null,
            null
        );
        
        assertEquals(symbol, response.symbol());
        assertEquals(quantity, response.quantity());
        assertEquals(averageCost, response.averageCost());
        assertNull(response.currentPrice());
        assertNull(response.marketValue());
        assertNull(response.unrealizedPnL());
    }
}
