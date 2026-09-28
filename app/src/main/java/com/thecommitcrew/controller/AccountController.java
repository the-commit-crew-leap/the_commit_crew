package com.thecommitcrew.controller;

import org.springframework.web.bind.annotation.RestController;

import com.thecommitcrew.domain.dto.AccountResponseDTO;
import com.thecommitcrew.domain.dto.BalanceResponseDTO;
import com.thecommitcrew.domain.dto.OrderResponseDTO;
import com.thecommitcrew.domain.dto.PositionResponseDTO;
import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Money;
import com.thecommitcrew.domain.model.Position;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.service.AccountService;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController 
@RequestMapping("/accounts") 
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponseDTO> getAccount(@PathVariable("id") Long accountId) {
        Account account = accountService.getAccount(accountId);
        AccountResponseDTO response = new AccountResponseDTO(
            account.getAccountId(),
            account.getStatus(),
            account.getCashBalance()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponseDTO> getAccountBalance(@PathVariable("id") Long accountId) {
        Money balance = accountService.getBalance(accountId);
        BalanceResponseDTO response = new BalanceResponseDTO(accountId, balance);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/positions")
    public ResponseEntity<List<PositionResponseDTO>> getAccountPositions(@PathVariable("id") Long accountId) {
        List<Position> positions = accountService.getPositions(accountId);
        List<PositionResponseDTO> response = positions.stream()
            .map(pos -> new PositionResponseDTO(
                pos.getSymbol(),
                pos.getQuantity(),
                pos.getAverageCost(),
                null,
                null,
                null
            ))
            .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/orders")
    public ResponseEntity<List<OrderResponseDTO>> getAccountOrders(@PathVariable("id") Long accountId) {
        List<Order> orders = accountService.getOrders(accountId);
        List<OrderResponseDTO> response = orders.stream()
            .map(order -> new OrderResponseDTO(
                order.getId(),
                order.getAccountId(),
                order.getSymbol(),
                order.getSide(),
                order.getQuantity(),
                order.getPrice(),
                order.getStatus(),
                order.getCreatedOn()
            ))
            .toList();
        return ResponseEntity.ok(response);
    }
}
