package com.thecommitcrew.domain.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.thecommitcrew.domain.enums.AccountStatus;
import com.thecommitcrew.domain.model.Money;

public class AccountResponseDTOTest {

    @Test
    void testAccountResponseCreation() {
        Long accountId = 1L;
        AccountStatus status = AccountStatus.ACTIVE;
        Money cashBalance = new Money(new BigDecimal("10000.00"));
        
        AccountResponseDTO response = new AccountResponseDTO(
            accountId,
            status,
            cashBalance
        );
        
        assertNotNull(response);
    }

    @Test
    void testAccountResponseFields() {
        Long accountId = 2L;
        AccountStatus status = AccountStatus.SUSPENDED;
        Money cashBalance = new Money(new BigDecimal("25000.50"));
        
        AccountResponseDTO response = new AccountResponseDTO(
            accountId,
            status,
            cashBalance
        );
        
        assertEquals(accountId, response.accountId());
        assertEquals(status, response.status());
        assertEquals(cashBalance, response.cashBalance());
        assertEquals(new BigDecimal("25000.50"), response.cashBalance().getAmount());
        assertEquals("USD", response.cashBalance().getCurrency());
    }
}
