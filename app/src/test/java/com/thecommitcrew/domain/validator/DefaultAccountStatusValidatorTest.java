package com.thecommitcrew.domain.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.thecommitcrew.domain.enums.AccountStatus;

class DefaultAccountStatusValidatorTest {

    private final DefaultAccountStatusValidator validator = new DefaultAccountStatusValidator();

    @Test
    void allowsDebitAndCreditForActiveAccounts() {
        assertDoesNotThrow(() -> validator.validateCanDebit(AccountStatus.ACTIVE));
        assertDoesNotThrow(() -> validator.validateCanCredit(AccountStatus.ACTIVE));
    }

    @Test
    void rejectsDebitForInactiveAccounts() {
        for (AccountStatus status : new AccountStatus[] {AccountStatus.SUSPENDED, AccountStatus.CLOSED}) {
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> validator.validateCanDebit(status)
            );
            assertEquals("Cannot debit from inactive account", exception.getMessage());
        }
    }

    @Test
    void rejectsCreditForInactiveAccounts() {
        for (AccountStatus status : new AccountStatus[] {AccountStatus.SUSPENDED, AccountStatus.CLOSED}) {
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> validator.validateCanCredit(status)
            );
            assertEquals("Cannot credit to inactive account", exception.getMessage());
        }
    }
}