package com.thecommitcrew.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thecommitcrew.domain.dto.OrderUpdate;
import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;
import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Instrument;
import com.thecommitcrew.domain.model.Money;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.domain.model.Position;
import com.thecommitcrew.messaging.ExecutionEvent;
import com.thecommitcrew.persistence.entity.AccountEntity;
import com.thecommitcrew.persistence.entity.InstrumentEntity;
import com.thecommitcrew.persistence.mapper.AccountMapper;
import com.thecommitcrew.persistence.mapper.InstrumentMapper;
import com.thecommitcrew.persistence.mapper.OrderMapper;
import com.thecommitcrew.persistence.mapper.PositionMapper;
import com.thecommitcrew.persistence.repository.AccountRepository;
import com.thecommitcrew.persistence.repository.InstrumentRepository;
import com.thecommitcrew.service.PositionService;

@ExtendWith(MockitoExtension.class)
class OrderSettlementServiceTest {

    private static final String ACCOUNT_ID = "ACC-1001";
    private static final String SYMBOL = "AAPL";
    private static final BigDecimal ORDER_PRICE = new BigDecimal("95.00");
    private static final BigDecimal FILL_PRICE = new BigDecimal("100.00");
    private static final Instant EXECUTED_ON = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountMapper accountMapper;
    @Mock
    private InstrumentRepository instrumentRepository;
    @Mock
    private InstrumentMapper instrumentMapper;
    @Mock
    private PositionMapper positionMapper;
    @Mock
    private PositionService positionService;
    @Mock
    private OrderUpdates orderUpdates;
    @Mock
    private Account account;
    @Mock
    private Account updatedAccount;
    @Mock
    private Instrument instrument;
    @Mock
    private AccountEntity accountEntity;
    @Mock
    private InstrumentEntity instrumentEntity;

    private OrderSettlementService settlementService;
    private Order order;
    private ExecutionEvent fill;
    private Position updatedPosition;

    @BeforeEach
    void setUp() {
        settlementService = new OrderSettlementService(
                orderMapper,
                accountRepository,
                accountMapper,
                instrumentRepository,
                instrumentMapper,
                positionMapper,
                positionService,
                orderUpdates);

        order = new Order(
                UUID.randomUUID(),
                ACCOUNT_ID,
                SYMBOL,
                OrderSide.BUY,
                10L,
                ORDER_PRICE,
                OrderStatus.NEW,
                LocalDateTime.now(),
                "idempotency-key");
        fill = new ExecutionEvent(
                UUID.randomUUID(),
                order.getId(),
                ACCOUNT_ID,
                SYMBOL,
                OrderSide.BUY,
                10,
                FILL_PRICE,
                ORDER_PRICE,
                "NYSE",
                EXECUTED_ON);
        updatedPosition = new Position(ACCOUNT_ID, SYMBOL, 10L, FILL_PRICE);
    }

    @Test
    void settle_ignoresUnknownOrder() {
        when(orderMapper.findById(fill.orderId())).thenReturn(Optional.empty());

        settlementService.settle(fill);

        verify(orderMapper).findById(fill.orderId());
        verifyNoInteractions(accountRepository, instrumentRepository, positionMapper, orderUpdates);
    }

    @Test
    void settle_ignoresOrderThatIsNotNew() {
        order.setStatus(OrderStatus.FILLED);
        when(orderMapper.findById(order.getId())).thenReturn(Optional.of(order));

        settlementService.settle(fill);

        verify(orderMapper).findById(order.getId());
        verifyNoInteractions(accountRepository, instrumentRepository, positionMapper, orderUpdates);
    }

    @Test
    void settle_rejectsPartialFill() {
        fill = executionWithQuantity(5);
        when(orderMapper.findById(order.getId())).thenReturn(Optional.of(order));

        settlementService.settle(fill);

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        verify(orderMapper).save(order);
        verifyRejectedUpdate("Partial fills are not supported: filled 5 of 10");
        verifyNoInteractions(accountRepository, instrumentRepository, positionMapper);
    }

    @Test
    void settle_rejectsNonTradableInstrument() {
        givenAccountAndInstrument(false);

        settlementService.settle(fill);

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        verify(orderMapper).save(order);
        verifyRejectedUpdate("Instrument is not tradable: " + SYMBOL);
        verifyNoInteractions(positionMapper, positionService);
    }

    @Test
    void settle_fillsBuyOrderAndPublishesUpdate() {
        givenAccountAndInstrument(true);
        when(positionMapper.findByAccountIdAndSymbol(ACCOUNT_ID, SYMBOL)).thenReturn(Optional.empty());
        when(positionService.applyOrder(any(Position.class), any(Order.class), eq(FILL_PRICE))).thenReturn(updatedPosition);
        when(account.debit(any(Money.class))).thenReturn(updatedAccount);
        when(updatedAccount.getCashBalance()).thenReturn(new Money(new BigDecimal("9000.00")));

        settlementService.settle(fill);

        assertEquals(OrderStatus.FILLED, order.getStatus());
        verify(accountEntity).setCashBalance(new BigDecimal("9000.00"));
        verify(accountRepository).save(accountEntity);
        verify(positionMapper).save(updatedPosition);
        verify(orderMapper).save(order);

        ArgumentCaptor<OrderUpdate> updateCaptor = ArgumentCaptor.forClass(OrderUpdate.class);
        verify(orderUpdates).publish(updateCaptor.capture());
        OrderUpdate update = updateCaptor.getValue();
        assertEquals(order.getId(), update.orderId());
        assertEquals(ACCOUNT_ID, update.accountId());
        assertEquals(SYMBOL, update.symbol());
        assertEquals(OrderSide.BUY, update.side());
        assertEquals(10, update.quantity());
        assertEquals(OrderStatus.FILLED, update.status());
        assertEquals(FILL_PRICE, update.fillPrice());
        assertEquals("Order filled at " + FILL_PRICE, update.message());
        assertEquals(EXECUTED_ON, update.timestamp());
    }

    @Test
    void settle_deletesPositionWhenSellClosesHolding() {
        order = new Order(
                order.getId(),
                ACCOUNT_ID,
                SYMBOL,
                OrderSide.SELL,
                10L,
                ORDER_PRICE,
                OrderStatus.NEW,
                order.getCreatedOn(),
                order.getIdempotencyKey());
        fill = new ExecutionEvent(
                fill.executionId(),
                order.getId(),
                ACCOUNT_ID,
                SYMBOL,
                OrderSide.SELL,
                10,
                FILL_PRICE,
                ORDER_PRICE,
                "NYSE",
                EXECUTED_ON);
        givenAccountAndInstrument(true);
        Position existingPosition = new Position(ACCOUNT_ID, SYMBOL, 10L, new BigDecimal("80.00"));
        when(positionMapper.findByAccountIdAndSymbol(ACCOUNT_ID, SYMBOL))
                .thenReturn(Optional.of(existingPosition));
        Position closedPosition = new Position(ACCOUNT_ID, SYMBOL, 0L, BigDecimal.ZERO);
        when(positionService.applyOrder(existingPosition, order, FILL_PRICE)).thenReturn(closedPosition);
        when(account.credit(any(Money.class))).thenReturn(updatedAccount);
        when(updatedAccount.getCashBalance()).thenReturn(new Money(new BigDecimal("9000.00")));

        settlementService.settle(fill);

        assertEquals(OrderStatus.FILLED, order.getStatus());
        verify(positionMapper).deleteByAccountIdAndSymbol(ACCOUNT_ID, SYMBOL);
        verify(positionMapper, never()).save(any(Position.class));
        verify(orderMapper).save(order);
        verify(orderUpdates).publish(any(OrderUpdate.class));
    }

    @Test
    void settle_rejectsWhenAccountCannotBeUpdated() {
        givenAccountAndInstrument(true);
        when(positionMapper.findByAccountIdAndSymbol(ACCOUNT_ID, SYMBOL)).thenReturn(Optional.empty());
        when(positionService.applyOrder(any(Position.class), any(Order.class), eq(FILL_PRICE))).thenReturn(updatedPosition);
        when(account.debit(any(Money.class)))
                .thenThrow(new IllegalArgumentException("Insufficient funds"));

        settlementService.settle(fill);

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        verify(orderMapper).save(order);
        verifyRejectedUpdate("Failed to update account balance: Insufficient funds");
        verify(accountRepository, never()).save(any(AccountEntity.class));
        verify(positionMapper, never()).save(any(Position.class));
    }

    @Test
    void settle_wrapsPersistenceFailure() {
        givenAccountAndInstrument(true);
        when(positionMapper.findByAccountIdAndSymbol(ACCOUNT_ID, SYMBOL)).thenReturn(Optional.empty());
        when(positionService.applyOrder(any(Position.class), any(Order.class), eq(FILL_PRICE))).thenReturn(updatedPosition);
        when(account.debit(any(Money.class))).thenReturn(updatedAccount);
        when(updatedAccount.getCashBalance()).thenReturn(new Money(new BigDecimal("9000.00")));
        when(accountRepository.save(accountEntity)).thenThrow(new IllegalStateException("database unavailable"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> settlementService.settle(fill));

        assertEquals("Failed to persist settlement for order " + order.getId(), exception.getMessage());
        assertEquals(OrderStatus.NEW, order.getStatus());
        verifyNoInteractions(orderUpdates);
    }

    private void givenAccountAndInstrument(boolean tradable) {
        when(orderMapper.findById(order.getId())).thenReturn(Optional.of(order));
        when(accountRepository.findByAccountId(ACCOUNT_ID))
                .thenReturn(Optional.of(accountEntity));
        when(accountMapper.toDomain(accountEntity)).thenReturn(account);
        when(instrumentRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(instrumentEntity));
        when(instrumentMapper.toDomain(instrumentEntity)).thenReturn(instrument);
        when(instrument.isTradable()).thenReturn(tradable);
    }

    private ExecutionEvent executionWithQuantity(int quantity) {
        return new ExecutionEvent(
                fill.executionId(),
                fill.orderId(),
                fill.accountId(),
                fill.symbol(),
                fill.side(),
                quantity,
                fill.price(),
                fill.limitPrice(),
                fill.venue(),
                fill.executedOn());
    }

    private void verifyRejectedUpdate(String message) {
        ArgumentCaptor<OrderUpdate> updateCaptor = ArgumentCaptor.forClass(OrderUpdate.class);
        verify(orderUpdates).publish(updateCaptor.capture());
        OrderUpdate update = updateCaptor.getValue();
        assertEquals(order.getId(), update.orderId());
        assertEquals(OrderStatus.REJECTED, update.status());
        assertEquals(null, update.fillPrice());
        assertEquals(message, update.message());
        assertEquals(EXECUTED_ON, update.timestamp());
    }
}
