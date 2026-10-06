package com.thecommitcrew.domain.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.thecommitcrew.domain.enums.AccountStatus;
import com.thecommitcrew.domain.model.Money;

class AccountResponseDTOTest {

    @Test
    void testAccountResponseCreation() {
        String accountId = "ACC-1001";
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
        String accountId = "ACC-1002";
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
