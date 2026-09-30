package com.thecommitcrew.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thecommitcrew.domain.dto.PlaceOrderRequestDTO;
import com.thecommitcrew.domain.enums.AccountStatus;
import com.thecommitcrew.domain.enums.AssetClass;
import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;
import com.thecommitcrew.domain.exception.AccountNotFoundException;
import com.thecommitcrew.domain.exception.DuplicateOrderException;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.domain.exception.NegativePriceException;
import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Instrument;
import com.thecommitcrew.domain.model.Money;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.domain.validator.BasicInstrumentSymbolValidator;
import com.thecommitcrew.domain.validator.DefaultAccountStatusValidator;
import com.thecommitcrew.persistence.repository.AccountRepository;
import com.thecommitcrew.persistence.repository.InstrumentRepository;
import com.thecommitcrew.persistence.entity.AccountEntity;
import com.thecommitcrew.persistence.entity.InstrumentEntity;
import com.thecommitcrew.persistence.mapper.AccountMapper;
import com.thecommitcrew.persistence.mapper.InstrumentMapper;
import com.thecommitcrew.persistence.mapper.OrderMapper;
import com.thecommitcrew.persistence.mapper.PositionMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class OrderServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final String SYMBOL = "AAPL";
    private static final String IDEMPOTENCY_KEY = "idem-123";

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PositionMapper positionMapper;
    @Mock
    private InstrumentRepository instrumentRepository;
    @Mock
    private AccountMapper accountMapper;
    @Mock
    private InstrumentMapper instrumentMapper;

    private OrderService orderService;
    private Account activeAccount;
    private AccountEntity activeAccountEntity;
    private Instrument tradableInstrument;
    private InstrumentEntity tradableInstrumentEntity;

    @BeforeEach
    void setUp() {
        // Removed PositionService from constructor
        orderService = new OrderService(orderMapper, accountRepository, positionMapper, 
                                       instrumentRepository, accountMapper, instrumentMapper);
        
        activeAccount = new Account(
            ACCOUNT_ID,
            "Jane Doe",
            new Money(new BigDecimal("10000.00")),
            AccountStatus.ACTIVE,
            1,
            LocalDateTime.now(),
            new DefaultAccountStatusValidator()
        );
        
        activeAccountEntity = new AccountEntity(
            ACCOUNT_ID.toString(),
            "Jane Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            1,
            LocalDateTime.now()
        );

        when(instrumentRepository.findBySymbol(SYMBOL))
            .thenReturn(Optional.of(new InstrumentEntity("AAPL", "Apple Inc.", null, "USD", true)));
        
        tradableInstrument = new Instrument(
            "instr-1",
            SYMBOL,
            "Apple Inc.",
            AssetClass.EQUITY,
            true,
            new BasicInstrumentSymbolValidator(instrumentRepository)
        );
        
        tradableInstrumentEntity = new InstrumentEntity(
            SYMBOL,
            "Apple Inc.",
            AssetClass.EQUITY,
            "USD",
            true
        );
    }

    @Test
    void placeOrder_buyWithFunds_createsNewOrder() {
        PlaceOrderRequestDTO request = request(ACCOUNT_ID, SYMBOL, OrderSide.BUY, 10L, "100.00", IDEMPOTENCY_KEY);

        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeAccountEntity));
        when(accountMapper.toDomain(activeAccountEntity)).thenReturn(activeAccount);
        when(instrumentMapper.toDomain(tradableInstrumentEntity)).thenReturn(tradableInstrument);
        when(instrumentRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(tradableInstrumentEntity));
        when(positionMapper.findByAccountIdAndSymbol(ACCOUNT_ID, SYMBOL)).thenReturn(Optional.empty());
        when(orderMapper.findByAccountId(ACCOUNT_ID)).thenReturn(List.of());
        doNothing().when(orderMapper).save(any(Order.class));

        Order order = orderService.placeOrder(request);

        assertEquals(OrderStatus.NEW, order.getStatus());
        assertEquals(ACCOUNT_ID, order.getAccountId());
        assertEquals(SYMBOL, order.getSymbol());
        verify(orderMapper, times(1)).save(order);
        verify(accountRepository, never()).save(any(AccountEntity.class));
        verify(positionMapper, never()).save(any());
    }

    @Test
    void placeOrder_buyWithoutFunds_rejectsOrder() {
        PlaceOrderRequestDTO request = request(ACCOUNT_ID, SYMBOL, OrderSide.BUY, 100L, "1000.00", IDEMPOTENCY_KEY);

        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeAccountEntity));
        when(accountMapper.toDomain(activeAccountEntity)).thenReturn(activeAccount);
        when(instrumentMapper.toDomain(tradableInstrumentEntity)).thenReturn(tradableInstrument);
        when(instrumentRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(tradableInstrumentEntity));
        when(positionMapper.findByAccountIdAndSymbol(ACCOUNT_ID, SYMBOL)).thenReturn(Optional.empty());
        when(orderMapper.findByAccountId(ACCOUNT_ID)).thenReturn(List.of());
        doNothing().when(orderMapper).save(any(Order.class));

        Order order = orderService.placeOrder(request);

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        verify(accountRepository, never()).save(any(AccountEntity.class));
        verify(positionMapper, never()).save(any());
    }

    @Test
    void placeOrder_missingAccount_throwsException() {
        PlaceOrderRequestDTO request = request(ACCOUNT_ID, SYMBOL, OrderSide.BUY, 10L, "100.00", IDEMPOTENCY_KEY);

        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> orderService.placeOrder(request));
    }

    @Test
    void cancelOrder_newOrder_marksCancelled() {
        UUID orderId = UUID.randomUUID();
        Order order = existingOrder(orderId, OrderSide.BUY, OrderStatus.NEW, 10L, "100.00", IDEMPOTENCY_KEY);

        when(orderMapper.findById(orderId)).thenReturn(Optional.of(order));

        orderService.cancelOrder(orderId);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderMapper).save(order);
    }

    @Test
    void cancelOrder_nonNewOrder_throwsException() {
        UUID orderId = UUID.randomUUID();
        Order order = existingOrder(orderId, OrderSide.BUY, OrderStatus.FILLED, 10L, "100.00", IDEMPOTENCY_KEY);

        when(orderMapper.findById(orderId)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> orderService.cancelOrder(orderId));
    }

    @Test
    void validateOrder_validRequest_doesNotThrow() {
        PlaceOrderRequestDTO request = request(ACCOUNT_ID, SYMBOL, OrderSide.BUY, 10L, "100.00", IDEMPOTENCY_KEY);

        when(instrumentRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(tradableInstrumentEntity));
        when(instrumentMapper.toDomain(tradableInstrumentEntity)).thenReturn(tradableInstrument);
        when(orderMapper.findByAccountId(ACCOUNT_ID)).thenReturn(List.of());

        assertDoesNotThrow(() -> orderService.validateOrder(request));
    }

    @Test
    void validateOrder_duplicateIdempotency_throwsException() {
        PlaceOrderRequestDTO request = request(ACCOUNT_ID, SYMBOL, OrderSide.BUY, 10L, "100.00", IDEMPOTENCY_KEY);
        Order existingOrder = existingOrder(UUID.randomUUID(), OrderSide.BUY, OrderStatus.NEW, 5L, "99.00", IDEMPOTENCY_KEY);

        when(instrumentRepository.findBySymbol(SYMBOL)).thenReturn(Optional.of(tradableInstrumentEntity));
        when(instrumentMapper.toDomain(tradableInstrumentEntity)).thenReturn(tradableInstrument);
        when(orderMapper.findByAccountId(ACCOUNT_ID)).thenReturn(List.of(existingOrder));

        assertThrows(DuplicateOrderException.class, () -> orderService.validateOrder(request));
    }

    @Test
    void validateOrder_missingInstrument_throwsException() {
        PlaceOrderRequestDTO request = request(ACCOUNT_ID, SYMBOL, OrderSide.BUY, 10L, "100.00", IDEMPOTENCY_KEY);

        when(instrumentRepository.findBySymbol(SYMBOL)).thenReturn(Optional.empty());

        assertThrows(InstrumentNotFoundException.class, () -> orderService.validateOrder(request));
    }

    @Test
    void validateOrder_negativePrice_throwsException() {
        PlaceOrderRequestDTO request = request(ACCOUNT_ID, SYMBOL, OrderSide.BUY, 10L, "-100.00", IDEMPOTENCY_KEY);

        assertThrows(NegativePriceException.class, () -> orderService.validateOrder(request));
    }

    private PlaceOrderRequestDTO request(Long accountId, String symbol, OrderSide side, long quantity,
                                         String price, String idempotencyKey) {
        return new PlaceOrderRequestDTO(
            accountId,
            symbol,
            side,
            quantity,
            new BigDecimal(price),
            idempotencyKey
        );
    }

    private Order existingOrder(UUID orderId, OrderSide side, OrderStatus status, long quantity,
                                String price, String idempotencyKey) {
        return new Order(
            orderId,
            ACCOUNT_ID,
            SYMBOL,
            side,
            quantity,
            new BigDecimal(price),
            status,
            LocalDateTime.now(),
            idempotencyKey
        );
    }
}