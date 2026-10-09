package com.thecommitcrew.service;

import java.util.List;
import com.thecommitcrew.persistence.repository.AccountRepository;
import com.thecommitcrew.domain.model.Order;
import org.springframework.stereotype.Service;
import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Position;
import com.thecommitcrew.domain.model.Money;
import com.thecommitcrew.domain.exception.AccountNotFoundException;
import com.thecommitcrew.persistence.entity.AccountEntity;
import com.thecommitcrew.persistence.mapper.AccountMapper;
import com.thecommitcrew.persistence.mapper.PositionMapper;
import com.thecommitcrew.persistence.mapper.OrderMapper;

@Service 
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final PositionMapper positionMapper;
    private final OrderMapper orderMapper;

    public AccountService(AccountRepository accountRepository, PositionMapper positionMapper, OrderMapper orderMapper, AccountMapper accountMapper) {
        this.accountRepository = accountRepository;
        this.positionMapper = positionMapper;
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
    }

    public Account getAccount(String accountId) {
        return accountRepository.findByAccountId(accountId)
            .map(accountMapper::toDomain)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    public List<Position> getPositions(String accountId) {
        getAccount(accountId); // Validate account exists
        return positionMapper.findByAccountId(accountId);
    }

    public List<Order> getOrders(String accountId) {
        getAccount(accountId); // Validate account exists
        return orderMapper.findByAccountId(accountId);
    }

    public Money getBalance(String accountId) {
        return getAccount(accountId).getCashBalance();
    }

    public Account deposit(String accountId, Money amount) {
        Account account = getAccount(accountId);
        Account updated = account.credit(amount);
        
        // Fetch the existing entity from DB
        AccountEntity existing = accountRepository.findByAccountId(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        // Update its fields with the new values
        existing.setCashBalance(updated.getCashBalance().getAmount());
        existing.setLastUpdated(updated.getLastUpdated());
        
        return accountMapper.toDomain(accountRepository.save(existing));
    }

    public Account withdraw(String accountId, Money amount) {
        Account account = getAccount(accountId);
        Account updated = account.debit(amount);
        
        // Fetch the existing entity from DB
        AccountEntity existing = accountRepository.findByAccountId(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        // Update its fields with the new values
        existing.setCashBalance(updated.getCashBalance().getAmount());
        existing.setLastUpdated(updated.getLastUpdated());
        
        return accountMapper.toDomain(accountRepository.save(existing));
    }
    
}