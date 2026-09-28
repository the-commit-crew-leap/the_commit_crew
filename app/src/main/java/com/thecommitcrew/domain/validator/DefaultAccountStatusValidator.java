package com.thecommitcrew.domain.validator;
import org.springframework.stereotype.Component;

import com.thecommitcrew.domain.enums.AccountStatus;

@Component 
public class DefaultAccountStatusValidator implements AccountStatusValidator {
    
    /**
     * Validates debit eligibility. Only active accounts can withdraw funds
     * to prevent unauthorized transactions on suspended or closed accounts.
     */
    @Override
    public void validateCanDebit(AccountStatus status) {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Cannot debit from inactive account");
        }
    }

    /**
     * Validates credit eligibility. Only active accounts can receive deposits
     * to maintain data integrity and comply with account lifecycle policies.
     */
    @Override
    public void validateCanCredit(AccountStatus status) {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Cannot credit to inactive account");
        }
    }
}