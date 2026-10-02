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
import com.thecommitcrew.service.PositionService;
import com.thecommitcrew.service.PriceService;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController 
@RequestMapping("/accounts/{id}") 
public class AccountController {
    private final AccountService accountService;
    private final PriceService priceService;
    private final PositionService positionService;

    public AccountController(AccountService accountService, PriceService priceService, PositionService positionService) {
        this.accountService = accountService;
        this.priceService = priceService;
        this.positionService = positionService;
    }

    @GetMapping
    public ResponseEntity<AccountResponseDTO> getAccount(@PathVariable("id") Long accountId) {
        Account account = accountService.getAccount(accountId);
        AccountResponseDTO response = new AccountResponseDTO(
            account.getAccountId(),
            account.getStatus(),
            account.getCashBalance()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponseDTO> getAccountBalance(@PathVariable("id") Long accountId) {
        Money balance = accountService.getBalance(accountId);
        BalanceResponseDTO response = new BalanceResponseDTO(accountId, balance);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/positions")
    public ResponseEntity<List<PositionResponseDTO>> getAccountPositions(@PathVariable("id") Long accountId) {
        List<Position> positions = accountService.getPositions(accountId);
        List<PositionResponseDTO> response = positions.stream()
            .map(pos -> {
                BigDecimal currentPrice = priceService.getCurrentPrice(pos.getSymbol());
                BigDecimal marketValue = positionService.marketValue(pos, currentPrice);
                BigDecimal unrealizedPnL = positionService.unrealizedProfitLoss(pos, currentPrice);
                BigDecimal unrealizedPnLPercent = positionService.unrealizedPnLPercent(pos, currentPrice);
                
                return new PositionResponseDTO(
                    pos.getSymbol(),
                    pos.getQuantity(),
                    pos.getAverageCost(),
                    currentPrice,
                    marketValue,
                    unrealizedPnL,
                    unrealizedPnLPercent
                );
            })
            .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders")
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
