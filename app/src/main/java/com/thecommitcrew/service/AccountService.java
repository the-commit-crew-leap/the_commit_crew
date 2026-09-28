package com.thecommitcrew.service;

import java.util.*;
import com.thecommitcrew.persistence.repository.AccountRepository;
import com.thecommitcrew.domain.model.Order;
import org.springframework.stereotype.Service;
import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Position;
import com.thecommitcrew.domain.model.Money;
import com.thecommitcrew.domain.exception.AccountNotFoundException;
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

    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
            .map(accountMapper::toDomain)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    public List<Position> getPositions(Long accountId) {
        getAccount(accountId); // Validate account exists
        return positionMapper.findByAccountId(accountId);
    }

    public List<Order> getOrders(Long accountId) {
        getAccount(accountId); // Validate account exists
        return orderMapper.findByAccountId(accountId);
    }

    public Money getBalance(Long accountId) {
        return getAccount(accountId).getCashBalance();
    }
    
}