package com.thecommitcrew.service;

import com.thecommitcrew.domain.dto.PlaceOrderRequestDTO;
import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;
import com.thecommitcrew.domain.exception.AccountNotActiveException;
import com.thecommitcrew.domain.exception.AccountNotFoundException;
import com.thecommitcrew.domain.exception.DuplicateOrderException;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.domain.exception.InsufficientFundsException;
import com.thecommitcrew.domain.exception.InsufficientHoldingsException;
import com.thecommitcrew.domain.exception.NegativePriceException;
import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Instrument;
import com.thecommitcrew.domain.model.Money;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.domain.model.Position;
import com.thecommitcrew.persistence.repository.AccountRepository;
import com.thecommitcrew.persistence.repository.InstrumentRepository;
import com.thecommitcrew.persistence.mapper.AccountMapper;
import com.thecommitcrew.persistence.mapper.InstrumentMapper;
import com.thecommitcrew.persistence.mapper.OrderMapper;
import com.thecommitcrew.persistence.mapper.PositionMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class OrderService {
    private static final long ZERO_QUANTITY = 0L;

    private final OrderMapper orderMapper;
    private final AccountRepository accountRepository;
    private final PositionMapper positionMapper;
    private final InstrumentRepository instrumentRepository;
    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final PositionService positionService;

    public OrderService(OrderMapper orderMapper, AccountRepository accountRepository,
                        PositionMapper positionMapper, InstrumentRepository instrumentRepository,
                        AccountMapper accountMapper, InstrumentMapper instrumentMapper,
                        PositionService positionService) {
        this.orderMapper = orderMapper;
        this.accountRepository = accountRepository;
        this.positionMapper = positionMapper;
        this.instrumentRepository = instrumentRepository;
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
        this.positionService = positionService;
    }

    @Transactional
    public Order placeOrder(PlaceOrderRequestDTO request) {
        Account account = getAccount(request.accountId());
        long availableHoldings = getAvailableHoldings(request.accountId(), request.symbol());

        Order order = new Order(
            UUID.randomUUID(),
            request.accountId(),
            request.symbol(),
            request.side(),
            request.quantity(),
            request.price(),
            OrderStatus.NEW,
            LocalDateTime.now(),
            request.idempotencyKey()
        );

        validateOrder(request);

        OrderStatus status = determineStatus(
            account,
            request.side(),
            request.quantity(),
            request.price(),
            availableHoldings
        );
        order.setStatus(status);

        orderMapper.save(order);
        if (order.getStatus() == OrderStatus.NEW) {
            executeLoadedOrder(order);
        }
        return order;
    }

    @Transactional
    public void cancelOrder(UUID orderId) {
        Order order = getOrder(orderId);
        if (order.getStatus() != OrderStatus.NEW) {
            throw new IllegalStateException("Only NEW orders can be cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderMapper.save(order);
    }

    public void validateOrder(PlaceOrderRequestDTO request) {
        validateOrder(new Order(
            UUID.randomUUID(),
            request.accountId(),
            request.symbol(),
            request.side(),
            request.quantity(),
            request.price(),
            OrderStatus.NEW,
            LocalDateTime.now(),
            request.idempotencyKey()
        ));
    }

    @Transactional
    public void executeOrder(UUID orderId) {
        executeLoadedOrder(getOrder(orderId));
    }

    private void validateOrder(Order order) {
        if (order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegativePriceException("Price must be positive");
        }

        Instrument instrument = instrumentRepository.findBySymbol(order.getSymbol())
            .map(instrumentMapper::toDomain)
            .orElseThrow(() -> new InstrumentNotFoundException(
                "Instrument not found for symbol: " + order.getSymbol()
        ));

        if (!instrument.isTradable()) {
            throw new IllegalStateException("Instrument is not tradable: " + order.getSymbol());
        }

        boolean duplicateOrder = orderMapper.findByAccountId(order.getAccountId()).stream()
            .anyMatch(existingOrder -> existingOrder.getIdempotencyKey().equals(order.getIdempotencyKey()));
        if (duplicateOrder) {
            throw new DuplicateOrderException(
                "Duplicate order detected for idempotency key: " + order.getIdempotencyKey()
            );
        }
    }

    private void executeLoadedOrder(Order order) {
        if (order.getStatus() != OrderStatus.NEW) {
            throw new IllegalStateException("Only NEW orders can be executed");
        }

        Account account = getAccount(order.getAccountId());
        if (!account.isActive()) {
            throw new AccountNotActiveException("Account is not active: " + order.getAccountId());
        }

        Optional<Position> existingPosition = positionMapper.findByAccountIdAndSymbol(
            order.getAccountId(),
            order.getSymbol()
        );
        long availableHoldings = existingPosition.map(Position::getQuantity).orElse(ZERO_QUANTITY);
        BigDecimal tradeValue = calculateTradeValue(order.getQuantity(), order.getPrice());

        if (order.getSide() == OrderSide.SELL && order.getQuantity() > availableHoldings) {
            throw new InsufficientHoldingsException(
                "Insufficient holdings to sell " + order.getQuantity() + " shares of " + order.getSymbol()
            );
        }

        if (order.getSide() == OrderSide.BUY && tradeValue.compareTo(account.getCashBalance().getAmount()) > 0) {
            throw new InsufficientFundsException(
                "Insufficient funds to buy " + order.getQuantity() + " shares of " + order.getSymbol()
            );
        }

        Money tradeAmount = new Money(tradeValue);
        Account updatedAccount = order.getSide() == OrderSide.BUY
            ? account.debit(tradeAmount)
            : account.credit(tradeAmount);
        com.thecommitcrew.persistence.entity.AccountEntity accountEntity = accountRepository.findById(order.getAccountId())
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + order.getAccountId()));
        accountEntity.setCashBalance(updatedAccount.getCashBalance().getAmount());
        accountRepository.save(accountEntity);

        Position basePosition = existingPosition.orElseGet(() -> new Position(
            order.getAccountId(),
            order.getSymbol(),
            ZERO_QUANTITY,
            BigDecimal.ZERO
        ));
        Position updatedPosition = positionService.applyOrder(basePosition, order);
        positionMapper.save(updatedPosition);

        order.setStatus(OrderStatus.FILLED);
        orderMapper.save(order);
    }

    private OrderStatus determineStatus(Account account, OrderSide side, long quantity, BigDecimal price,
                                        long availableHoldings) {
        if (!account.isActive()) {
            return OrderStatus.REJECTED;
        }
        if (side == OrderSide.SELL && quantity > availableHoldings) {
            return OrderStatus.REJECTED;
        }
        if (side == OrderSide.BUY
            && calculateTradeValue(quantity, price).compareTo(account.getCashBalance().getAmount()) > 0) {
            return OrderStatus.REJECTED;
        }
        return OrderStatus.NEW;
    }

    private Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
            .map(accountMapper::toDomain)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    private Order getOrder(UUID orderId) {
        return orderMapper.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    private long getAvailableHoldings(Long accountId, String symbol) {
        return positionMapper.findByAccountIdAndSymbol(accountId, symbol)
            .map(Position::getQuantity)
            .orElse(ZERO_QUANTITY);
    }

    private BigDecimal calculateTradeValue(long quantity, BigDecimal price) {
        return BigDecimal.valueOf(quantity).multiply(price);
    }
}