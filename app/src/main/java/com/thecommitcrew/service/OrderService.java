package com.thecommitcrew.service;

import com.thecommitcrew.domain.dto.PlaceOrderRequestDTO;
import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;
import com.thecommitcrew.domain.exception.AccountNotFoundException;
import com.thecommitcrew.domain.exception.DuplicateOrderException;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.domain.exception.NegativePriceException;
import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Instrument;
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
import java.time.ZoneId;
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
    private final PriceService priceService;

    public OrderService(OrderMapper orderMapper, AccountRepository accountRepository,
                        PositionMapper positionMapper, InstrumentRepository instrumentRepository,
                        AccountMapper accountMapper, InstrumentMapper instrumentMapper, PriceService priceService) {
        this.orderMapper = orderMapper;
        this.accountRepository = accountRepository;
        this.positionMapper = positionMapper;
        this.instrumentRepository = instrumentRepository;
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
        this.priceService = priceService;
    }

    @Transactional
    public Order placeOrder(PlaceOrderRequestDTO request) {
        Account account = getAccount(request.accountId());
        long availableHoldings = getAvailableHoldings(request.accountId(), request.symbol());

        BigDecimal marketPrice = priceService.getCurrentPrice(request.symbol());
        String idempotencyKey = UUID.randomUUID().toString();

        Order order = new Order(
            UUID.randomUUID(),
            request.accountId(),
            request.symbol(),
            request.side(),
            request.quantity(),
            marketPrice,
            OrderStatus.NEW,
            LocalDateTime.now(ZoneId.of("UTC")),
            idempotencyKey
        );

        validateOrder(order);

        OrderStatus status = determineStatus(
            account,
            request.side(),
            request.quantity(),
            marketPrice,
            availableHoldings
        );
        order.setStatus(status);

        orderMapper.save(order);
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

    private void validateOrder(Order order) {
        if (order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegativePriceException("Price must be positive");
        }

        Instrument instrument = instrumentRepository.findBySymbol(order.getSymbol())
            .map(instrumentMapper::toDomain)
            .orElseThrow(() -> new InstrumentNotFoundException(
                "Instrument not found for symbol: " + order.getSymbol()
        ));

        boolean tradable = instrument.isTradable();
        if (!tradable) {
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

    private Account getAccount(String accountId) {
        return accountRepository.findByAccountId(accountId)
            .map(accountMapper::toDomain)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    private Order getOrder(UUID orderId) {
        return orderMapper.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    private long getAvailableHoldings(String accountId, String symbol) {
        return positionMapper.findByAccountIdAndSymbol(accountId, symbol)
            .map(Position::getQuantity)
            .orElse(ZERO_QUANTITY);
    }

    private BigDecimal calculateTradeValue(long quantity, BigDecimal price) {
        return BigDecimal.valueOf(quantity).multiply(price);
    }
}