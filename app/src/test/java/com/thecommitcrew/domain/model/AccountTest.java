package com.thecommitcrew.domain.model;

import com.thecommitcrew.domain.enums.AccountStatus;
import com.thecommitcrew.domain.validator.AccountStatusValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AccountTest {
    
    private Account account;
    private static final AccountStatusValidator VALIDATOR = new DefaultAccountStatusValidator();
    
    private static final String TEST_ACCOUNT_ID = "ACC-1001";
    private static final String TEST_HOLDER_NAME = "John Doe";
    private static final int TEST_VERSION = 1;

    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("10000.00");
    private static final BigDecimal SMALL_AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal DEBIT_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal CREDIT_AMOUNT = new BigDecimal("5000.00");
    private static final BigDecimal INSUFFICIENT_AMOUNT = new BigDecimal("20000.00");
    private static final BigDecimal MEDIUM_AMOUNT = new BigDecimal("500.00");

    private Money createMoney(BigDecimal amount) {
        return new Money(amount);
    }

    @BeforeEach
    void setUp() {
        account = new Account(
            TEST_ACCOUNT_ID,
            TEST_HOLDER_NAME,
            createMoney(INITIAL_BALANCE),
            AccountStatus.ACTIVE,
            TEST_VERSION,
            LocalDateTime.now(),
            VALIDATOR
        );
    }

    @Test
    void testDebit_SuccessfulTransactionUpdatesBalance() {
        Money debitAmount = createMoney(DEBIT_AMOUNT);
        
        Account updatedAccount = account.debit(debitAmount);
        
        assertEquals(createMoney(new BigDecimal("9000.00")), updatedAccount.getCashBalance());
    }
    
    @Test
    void testDebit_ThrowsExceptionWhenInsufficientFunds() {
        Money debitAmount = createMoney(INSUFFICIENT_AMOUNT);
        
        assertThrows(IllegalArgumentException.class, () -> account.debit(debitAmount));
    }

    @Test
    void testCredit_SuccessfullyIncreasesBalance() {
        Money creditAmount = createMoney(CREDIT_AMOUNT);
        
        Account updatedAccount = account.credit(creditAmount);
        
        assertEquals(createMoney(INITIAL_BALANCE.add(CREDIT_AMOUNT)), updatedAccount.getCashBalance());
        // Original account should remain unchanged (immutable)
        assertEquals(account.getCashBalance(), createMoney(INITIAL_BALANCE));
    }
    
    @Test
    void testDebit_ThrowsExceptionWhenAccountIsInactive() {
        Account suspendedAccount = account.updateStatus(AccountStatus.SUSPENDED);
        Money debitAmount = createMoney(SMALL_AMOUNT);
        
        assertThrows(IllegalStateException.class, () -> suspendedAccount.debit(debitAmount));
    }

    @Test
    void testIsActive_ReturnsTrueForActiveAndFalseForInactiveStatuses() {
        assertTrue(account.isActive());
        
        Account suspendedAccount = account.updateStatus(AccountStatus.SUSPENDED);
        assertFalse(suspendedAccount.isActive());
        
        Account closedAccount = account.updateStatus(AccountStatus.CLOSED);
        assertFalse(closedAccount.isActive());
    }

    @Test
    void testVersionIncrement_IncrementsOnEveryOperation() {
        assertEquals(1L, account.getVersion());
        
        Account updatedAccount = account.debit(createMoney(new BigDecimal("100.00")));
        assertEquals(2L, updatedAccount.getVersion());
        
        Account creditAccount = updatedAccount.credit(createMoney(new BigDecimal("50.00")));
        assertEquals(3L, creditAccount.getVersion());
    }

    @Test
    void testEquals_IdentifiesAccountsByIdOnly() {
        assertEquals(account, new Account(TEST_ACCOUNT_ID, "Different Name",
            createMoney(MEDIUM_AMOUNT), AccountStatus.ACTIVE, TEST_VERSION,
            LocalDateTime.now(), VALIDATOR));
        
        assertNotEquals(account, new Account("ACC-1002", "Different Name",
            createMoney(MEDIUM_AMOUNT), AccountStatus.ACTIVE, TEST_VERSION,
            LocalDateTime.now(), VALIDATOR));
    }

    static class DefaultAccountStatusValidator implements AccountStatusValidator {
        @Override
        public void validateCanDebit(AccountStatus status) {
            if (status != AccountStatus.ACTIVE) {
                throw new IllegalStateException("Cannot debit from inactive account");
            }
        }

        @Override
        public void validateCanCredit(AccountStatus status) {
            if (status != AccountStatus.ACTIVE) {
                throw new IllegalStateException("Cannot credit to inactive account");
            }
        }
    }
}