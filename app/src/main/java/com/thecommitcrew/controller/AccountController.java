package com.thecommitcrew.controller;

import org.springframework.web.bind.annotation.RestController;

import com.thecommitcrew.domain.dto.AccountResponseDTO;
import com.thecommitcrew.domain.dto.BalanceResponseDTO;
import com.thecommitcrew.domain.dto.OrderResponseDTO;
import com.thecommitcrew.domain.dto.PositionResponseDTO;
import com.thecommitcrew.domain.dto.LivePriceResponse;
import com.thecommitcrew.domain.dto.IntradayPriceResponse;
import com.thecommitcrew.domain.dto.TransactionRequestDTO;
import com.thecommitcrew.domain.dto.TransactionResponseDTO;
import com.thecommitcrew.domain.exception.UnauthorizedAccountException;
import com.thecommitcrew.domain.exception.LivePriceUnavailableException;
import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Money;
import com.thecommitcrew.domain.model.Position;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.service.AccountService;
import com.thecommitcrew.service.PositionService;
import com.thecommitcrew.service.PriceService;
import com.thecommitcrew.service.InstrumentService;
import com.thecommitcrew.auth.CheckAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController 
@RequestMapping("/api/v1/accounts/{id}") 
public class AccountController {
    private final AccountService accountService;
    private final PriceService priceService;
    private final PositionService positionService;
    private final InstrumentService instrumentService;

    private static final String ACCOUNT_ID_ATTRIBUTE = "accountId";

    public AccountController(AccountService accountService, PriceService priceService, PositionService positionService,
            InstrumentService instrumentService) {
        this.accountService = accountService;
        this.priceService = priceService;
        this.positionService = positionService;
        this.instrumentService = instrumentService;
    }

    @GetMapping
    @CheckAuth
    public ResponseEntity<AccountResponseDTO> getAccount(
        @PathVariable("id") String accountId,
        HttpServletRequest httpRequest) {

        // Get authenticated user's account from JWT
        String userAccountId = (String) httpRequest.getAttribute(ACCOUNT_ID_ATTRIBUTE);
        
        // Verify user can only access their own account
        if (!accountId.equals(userAccountId)) {
            throw new UnauthorizedAccountException(
                "Forbidden: You do not have access to account " + accountId
            );
        }
        
        Account account = accountService.getAccount(accountId);
        AccountResponseDTO response = new AccountResponseDTO(
            account.getAccountId(),
            account.getStatus(),
            account.getCashBalance()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/balance")
    @CheckAuth
    public ResponseEntity<BalanceResponseDTO> getAccountBalance(
        @PathVariable("id") String accountId,
        HttpServletRequest httpRequest) {

        String userAccountId = (String) httpRequest.getAttribute(ACCOUNT_ID_ATTRIBUTE);
        if (!accountId.equals(userAccountId)) {
            throw new UnauthorizedAccountException(
                "Forbidden: You do not have access to account " + accountId
            );
        }

        Money balance = accountService.getBalance(accountId);
        BalanceResponseDTO response = new BalanceResponseDTO(accountId, balance);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/positions")
    @CheckAuth
    public ResponseEntity<List<PositionResponseDTO>> getAccountPositions(
        @PathVariable("id") String accountId,
        HttpServletRequest httpRequest) {

        String userAccountId = (String) httpRequest.getAttribute(ACCOUNT_ID_ATTRIBUTE);
        if (!accountId.equals(userAccountId)) {
            throw new UnauthorizedAccountException(
                "Forbidden: You do not have access to account " + accountId
            );
        }
        
        List<Position> positions = accountService.getPositions(accountId);
        List<PositionResponseDTO> response = positions.stream()
            .map(pos -> {
                LivePriceResponse livePrice;
                try {
                    livePrice = priceService.getCurrentLivePrice(pos.getSymbol());
                    if (!isCurrentRegularSession(livePrice.updatedAt())) {
                        return lastAvailablePosition(pos);
                    }
                } catch (LivePriceUnavailableException exception) {
                    return lastAvailablePosition(pos);
                }

                BigDecimal currentPrice = livePrice.price();
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
                    unrealizedPnLPercent,
                    livePrice.source(),
                    livePrice.updatedAt(),
                    livePrice.live()
                );
            })
            .toList();
        return ResponseEntity.ok(response);
    }

    private PositionResponseDTO unavailablePosition(Position position) {
        return new PositionResponseDTO(
            position.getSymbol(),
            position.getQuantity(),
            position.getAverageCost(),
            null,
            null,
            null,
            null,
            null,
            null,
            false
        );
    }

    private PositionResponseDTO lastAvailablePosition(Position position) {
        try {
            IntradayPriceResponse intraday = priceService.getIntradayPrices(position.getSymbol());
            if (intraday.bars().isEmpty()) {
                return databaseLastAvailablePosition(position);
            }
            IntradayPriceResponse.IntradayBar lastBar = intraday.bars().get(intraday.bars().size() - 1);
            BigDecimal currentPrice = lastBar.close();
            return new PositionResponseDTO(
                position.getSymbol(),
                position.getQuantity(),
                position.getAverageCost(),
                currentPrice,
                positionService.marketValue(position, currentPrice),
                positionService.unrealizedProfitLoss(position, currentPrice),
                positionService.unrealizedPnLPercent(position, currentPrice),
                intraday.source(),
                intraday.updatedAt(),
                false
            );
        } catch (RuntimeException fallbackException) {
            return databaseLastAvailablePosition(position);
        }
    }

    private PositionResponseDTO databaseLastAvailablePosition(Position position) {
        try {
            LivePriceResponse lastClose = priceService.getLastAvailableClose(position.getSymbol());
            BigDecimal currentPrice = lastClose.price();
            return new PositionResponseDTO(
                position.getSymbol(),
                position.getQuantity(),
                position.getAverageCost(),
                currentPrice,
                positionService.marketValue(position, currentPrice),
                positionService.unrealizedProfitLoss(position, currentPrice),
                positionService.unrealizedPnLPercent(position, currentPrice),
                lastClose.source(),
                lastClose.updatedAt(),
                false
            );
        } catch (RuntimeException unavailableException) {
            return unavailablePosition(position);
        }
    }

    private boolean isCurrentRegularSession(java.time.Instant tradeTimestamp) {
        if (tradeTimestamp == null) {
            return false;
        }
        ZoneId eastern = ZoneId.of("America/New_York");
        ZonedDateTime now = ZonedDateTime.now(eastern);
        ZonedDateTime tradeTime = tradeTimestamp.atZone(eastern);
        LocalTime currentTime = now.toLocalTime();
        LocalTime marketOpen = LocalTime.of(9, 30);
        LocalTime marketClose = LocalTime.of(16, 0);
        return now.getDayOfWeek().getValue() <= 5
            && tradeTime.toLocalDate().equals(now.toLocalDate())
            && !currentTime.isBefore(marketOpen)
            && currentTime.isBefore(marketClose)
            && !tradeTime.toLocalTime().isBefore(marketOpen)
            && tradeTime.toLocalTime().isBefore(marketClose);
    }

    @GetMapping("/orders")
    @CheckAuth
    public ResponseEntity<List<OrderResponseDTO>> getAccountOrders(
        @PathVariable("id") String accountId,
        HttpServletRequest httpRequest) {

        String userAccountId = (String) httpRequest.getAttribute(ACCOUNT_ID_ATTRIBUTE);
        if (!accountId.equals(userAccountId)) {
            throw new UnauthorizedAccountException(
                "Forbidden: You do not have access to account " + accountId
            );
        }
        
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
                order.getCreatedOn(),
                instrumentService.getInstrument(order.getSymbol()).assetClass()
            ))
            .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/deposit")
    @CheckAuth
    public ResponseEntity<TransactionResponseDTO> deposit(
        @PathVariable("id") String accountId,
        @Valid @RequestBody TransactionRequestDTO request,
        HttpServletRequest httpRequest) {

        String userAccountId = (String) httpRequest.getAttribute(ACCOUNT_ID_ATTRIBUTE);
        if (!accountId.equals(userAccountId)) {
            throw new UnauthorizedAccountException(
                "Forbidden: You do not have access to account " + accountId
            );
        }

        Money amount = new Money(request.amount());
        Account updated = accountService.deposit(accountId, amount);

        TransactionResponseDTO response = new TransactionResponseDTO(
            updated.getAccountId(),
            "DEPOSIT",
            request.amount(),
            updated.getCashBalance(),
            LocalDateTime.now(ZoneId.of("UTC"))
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/withdraw")
    @CheckAuth
    public ResponseEntity<TransactionResponseDTO> withdraw(
        @PathVariable("id") String accountId,
        @Valid @RequestBody TransactionRequestDTO request,
        HttpServletRequest httpRequest) {

        String userAccountId = (String) httpRequest.getAttribute(ACCOUNT_ID_ATTRIBUTE);
        if (!accountId.equals(userAccountId)) {
            throw new UnauthorizedAccountException(
                "Forbidden: You do not have access to account " + accountId
            );
        }

        Money amount = new Money(request.amount());
        Account updated = accountService.withdraw(accountId, amount);

        TransactionResponseDTO response = new TransactionResponseDTO(
            updated.getAccountId(),
            "WITHDRAW",
            request.amount(),
            updated.getCashBalance(),
            LocalDateTime.now(ZoneId.of("UTC"))
        );
        return ResponseEntity.ok(response);
    }
}
