package com.thecommitcrew.domain.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;

import org.junit.jupiter.api.Test;

public class OrderResponseDTOTest {
    
    @Test
    void testOrderResponseCreation() {
        UUID orderId = UUID.randomUUID();
        Long accountId = 1L;
        String symbol = "AAPL";
        OrderSide side = OrderSide.BUY;
        long quantity = 100L;
        BigDecimal price = new BigDecimal("150.00");
        OrderStatus status = OrderStatus.FILLED;
        LocalDateTime createdOn = LocalDateTime.now();
        
        OrderResponseDTO response = new OrderResponseDTO(
            orderId,
            accountId,
            symbol,
            side,
            quantity,
            price,
            status,
            createdOn
        );
        
        assertNotNull(response);
        assertEquals(orderId, response.orderId());
        assertEquals(accountId, response.accountId());
    }
    
    @Test
    void testOrderResponseFields() {
        UUID orderId = UUID.randomUUID();
        Long accountId = 1L;
        String symbol = "AAPL";
        OrderSide side = OrderSide.SELL;
        long quantity = 50L;
        BigDecimal price = new BigDecimal("155.50");
        OrderStatus status = OrderStatus.NEW;
        LocalDateTime createdOn = LocalDateTime.now();
        
        OrderResponseDTO response = new OrderResponseDTO(
            orderId,
            accountId,
            symbol,
            side,
            quantity,
            price,
            status,
            createdOn
        );
        
        assertEquals(orderId, response.orderId());
        assertEquals(accountId, response.accountId());
        assertEquals(symbol, response.symbol());
        assertEquals(side, response.side());
        assertEquals(quantity, response.quantity());
        assertEquals(price, response.price());
        assertEquals(status, response.status());
        assertEquals(createdOn, response.createdOn());
    }
}
