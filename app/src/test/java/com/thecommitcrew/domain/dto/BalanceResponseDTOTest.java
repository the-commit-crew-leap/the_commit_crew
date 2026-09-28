package com.thecommitcrew.domain.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.thecommitcrew.domain.model.Money;

public class BalanceResponseDTOTest {
    
    @Test
    void testBalanceResponseCreation() {
        Long accountId = 1L;
        Money cashBalance = new Money(new BigDecimal("10000.00"));
        
        BalanceResponseDTO response = new BalanceResponseDTO(
            accountId,
            cashBalance
        );
        
        assertNotNull(response);
        assertEquals(accountId, response.accountId());
    }
    
    @Test
    void testBalanceResponseFields() {
        Long accountId = 2L;
        Money cashBalance = new Money(new BigDecimal("25000.50"));
        
        BalanceResponseDTO response = new BalanceResponseDTO(
            accountId,
            cashBalance
        );
        
        assertEquals(accountId, response.accountId());
        assertEquals(cashBalance, response.cashBalance());
        assertEquals(new BigDecimal("25000.50"), response.cashBalance().getAmount());
        assertEquals("USD", response.cashBalance().getCurrency());
    }
}
