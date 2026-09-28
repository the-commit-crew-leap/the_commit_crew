package com.thecommitcrew.persistence.mapper;

import org.springframework.stereotype.Component;

import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Money;
import com.thecommitcrew.domain.validator.AccountStatusValidator;
import com.thecommitcrew.persistence.entity.AccountEntity;

@Component
public class AccountMapper {

    private final AccountStatusValidator statusValidator;
    
    public AccountMapper(AccountStatusValidator statusValidator) {
        this.statusValidator = statusValidator;
    }
    
    // Convert entity to domain model
    public Account toDomain(AccountEntity entity) {
        if (entity == null) return null;
        
        Money cashBalance = new Money(entity.getCashBalance());
        
        return new Account(
            entity.getId(),
            entity.getHolderName(),
            cashBalance,
            entity.getStatus(),
            entity.getVersion(),
            entity.getLastUpdated(),
            statusValidator
        );
    }
    
    // Convert domain model to entity
    public AccountEntity toEntity(Account domain) {
        if (domain == null) return null;
        
        return new AccountEntity(
            domain.getAccountId().toString(),
            domain.getHolderName(),
            domain.getCashBalance().getAmount(),
            domain.getStatus(),
            domain.getVersion(),
            domain.getLastUpdated()
        );
    }

}
