package com.thecommitcrew.application;

import com.thecommitcrew.domain.dto.OrderUpdate;
import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Instrument;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.domain.model.Position;
import com.thecommitcrew.messaging.ExecutionEvent;
import com.thecommitcrew.persistence.mapper.AccountMapper;
import com.thecommitcrew.persistence.mapper.InstrumentMapper;
import com.thecommitcrew.persistence.mapper.OrderMapper;
import com.thecommitcrew.persistence.mapper.PositionMapper;
import com.thecommitcrew.persistence.repository.AccountRepository;
import com.thecommitcrew.persistence.repository.InstrumentRepository;
import com.thecommitcrew.service.PositionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Step 3 of the event flow: applies a fill from the {@code executions}
 * topic. In one transaction it locks the order, account and position,
 * re-runs the business rules at the fill price (the account may have
 * changed since the order was accepted), persists cash and position,
 * records the execution and marks the order FILLED. The trader is told once
 * that commits.
 *
 * <p>Kafka delivers at least once, so the same fill, or a second fill for a
 * republished order, can arrive twice. Only an order that is still NEW is
 * settled; anything else is logged and ignored.
 */
@Service
public class OrderSettlementService {

    private static final Logger log = LoggerFactory.getLogger(OrderSettlementService.class);

    private final OrderMapper orderMapper;
    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final InstrumentRepository instrumentRepository;
    private final InstrumentMapper instrumentMapper;
    private final PositionMapper positionMapper;
    private final PositionService positionService;
    private final OrderUpdates orderUpdates;

    public OrderSettlementService(OrderMapper orderMapper,
                                  AccountRepository accountRepository,
                                  AccountMapper accountMapper,
                                  InstrumentRepository instrumentRepository,
                                  InstrumentMapper instrumentMapper,
                                  PositionMapper positionMapper,
                                  PositionService positionService,
                                  OrderUpdates orderUpdates) {
        this.orderMapper = orderMapper;
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
        this.instrumentRepository = instrumentRepository;
        this.instrumentMapper = instrumentMapper;
        this.positionMapper = positionMapper;
        this.positionService = positionService;
        this.orderUpdates = orderUpdates;
    }

    @Transactional
    public void settle(ExecutionEvent fill) {
        Long accountId = fill.accountId();

        Optional<Order> orderOpt = orderMapper.findById(fill.orderId());
        if (orderOpt.isEmpty()) {
            log.warn("Ignoring execution {} for unknown order {}", fill.executionId(), fill.orderId());
            return;
        }

        Order order = orderOpt.get();
        if (order.getStatus() != OrderStatus.NEW) {
            log.info("Ignoring duplicate execution {} for order {} (already {})",
                    fill.executionId(), order.getId(), order.getStatus());
            return;
        }

        if (fill.quantity() != order.getQuantity()) {
            reject(order, fill, "Partial fills are not supported: filled " + fill.quantity()
                    + " of " + order.getQuantity());
            return;
        }

        Account account = accountRepository.findById(accountId)
                .map(accountMapper::toDomain)
                .orElseThrow(() -> new IllegalStateException("Order " + order.getId() + " has no account"));

        Instrument instrument = instrumentRepository.findBySymbol(order.getSymbol())
                .map(instrumentMapper::toDomain)
                .orElseThrow(() -> new InstrumentNotFoundException("Instrument not found for symbol: " + order.getSymbol()));

        boolean tradable = instrument.isTradable();
        if (!tradable) {
            reject(order, fill, "Instrument is not tradable: " + order.getSymbol());
            return;
        }

        Optional<Position> existingPosition = positionMapper.findByAccountIdAndSymbol(accountId, order.getSymbol());
        Position position = existingPosition.orElse(new Position(accountId, order.getSymbol(), 0L, BigDecimal.ZERO));

        Position updatedPosition = positionService.applyOrder(position, order);

        BigDecimal value = fill.price().multiply(BigDecimal.valueOf(fill.quantity()));
        Account updatedAccount;
        try {
            if (order.getSide() == OrderSide.BUY) {
                updatedAccount = account.debit(new com.thecommitcrew.domain.model.Money(value));
            } else {
                updatedAccount = account.credit(new com.thecommitcrew.domain.model.Money(value));
            }
        } catch (Exception e) {
            reject(order, fill, "Failed to update account balance: " + e.getMessage());
            return;
        }

        try {
            var accountEntity = accountRepository.findById(accountId)
                    .orElseThrow(() -> new IllegalStateException("Account not found"));
            accountEntity.setCashBalance(updatedAccount.getCashBalance().getAmount());
            accountRepository.save(accountEntity);

            positionMapper.save(updatedPosition);

            order.setStatus(OrderStatus.FILLED);
            orderMapper.save(order);
        } catch (Exception e) {
            log.error("Failed to persist settlement for order {}: {}", order.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to persist settlement for order " + order.getId(), e);
        }

        log.info("Order {} FILLED: {} {} {} @ {} ({})",
                order.getId(), order.getSide(), order.getQuantity(),
                order.getSymbol(), fill.price(), fill.venue());

        String message = "Order filled at " + fill.price();
        AfterCommit.run(() -> orderUpdates.publish(update(order, fill, OrderStatus.FILLED, fill.price(), message)));
    }

    private void reject(Order order, ExecutionEvent fill, String reason) {
        try {
            order.setStatus(OrderStatus.REJECTED);
            orderMapper.save(order);
            log.info("Order {} REJECTED at settlement: {}", order.getId(), reason);
            AfterCommit.run(() -> orderUpdates.publish(update(order, fill, OrderStatus.REJECTED, null, reason)));
        } catch (Exception e) {
            log.error("Failed to reject order {}: {}", order.getId(), e.getMessage(), e);
        }
    }

    private OrderUpdate update(Order order, ExecutionEvent fill, OrderStatus status,
                               BigDecimal fillPrice, String message) {
        return new OrderUpdate(
                order.getId(),
                fill.accountId(),
                order.getSymbol(),
                order.getSide(),
                (int) order.getQuantity(),
                order.getPrice(),
                status,
                fillPrice,
                message,
                fill.executedOn()
        );
    }
}