package com.thecommitcrew.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.thecommitcrew.domain.dto.PlaceOrderRequestDTO;
import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;
import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Instrument;
import com.thecommitcrew.domain.model.Money;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.domain.validator.AccountStatusValidator;
import com.thecommitcrew.domain.validator.InstrumentSymbolValidator;
import com.thecommitcrew.domain.exception.AccountNotFoundException;
import com.thecommitcrew.domain.exception.DuplicateOrderException;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.persistence.entity.AccountEntity;
import com.thecommitcrew.persistence.entity.InstrumentEntity;
import com.thecommitcrew.persistence.repository.AccountRepository;
import com.thecommitcrew.persistence.repository.InstrumentRepository;
import com.thecommitcrew.persistence.mapper.PositionMapper;
import com.thecommitcrew.persistence.mapper.OrderMapper;
import com.thecommitcrew.persistence.mapper.AccountMapper;
import com.thecommitcrew.persistence.mapper.InstrumentMapper;
import com.thecommitcrew.domain.enums.AccountStatus;
import com.thecommitcrew.domain.enums.AssetClass;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Characterization Tests for Order Placement Path
 * 
 * These tests document and pin the current behavior of the order placement workflow.
 * They verify:
 * - Order status transitions (NEW, FILLED, REJECTED)
 * - Exception handling for invalid inputs
 * - Core business logic validation
 */
@SpringBootTest
@Transactional
@DisplayName("Order Placement Characterization Tests")
class OrderPlacementCharacterizationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @MockBean
    private PositionMapper positionMapper;

    @MockBean
    private OrderMapper orderMapper;

    @MockBean
    private AccountMapper accountMapper;

    @MockBean
    private InstrumentMapper instrumentMapper;

    // Test data constants
    private static final long TEST_ACCOUNT_ID = 1L;  // Existing account in database
    private static final String TEST_SYMBOL = "AAPL";
    
    // In-memory storage for orders saved during test
    private List<Order> savedOrders;

    @BeforeEach
    void setUp() {
        // Initialize order storage for this test
        savedOrders = new ArrayList<>();
        
        // Ensure account 1 exists and is ACTIVE
        AccountEntity account = accountRepository.findById(TEST_ACCOUNT_ID)
            .orElseGet(() -> {
                // Create if doesn't exist
                AccountEntity newAccount = new AccountEntity();
                newAccount.setAccountId("ACC-TEST-1");
                newAccount.setHolderName("Test Account");
                newAccount.setCashBalance(BigDecimal.valueOf(100000.00));
                newAccount.setStatus(AccountStatus.ACTIVE);
                newAccount.setLastUpdated(LocalDateTime.now());
                return accountRepository.save(newAccount);
            });
        
        // Ensure ACTIVE status
        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);

        // Create test instrument if needed
        if (instrumentRepository.findBySymbol(TEST_SYMBOL).isEmpty()) {
            InstrumentEntity instrument = new InstrumentEntity();
            instrument.setSymbol(TEST_SYMBOL);
            instrument.setName("Characterization Test");
            instrument.setAssetClass(AssetClass.EQUITY);  // Required field
            instrument.setCurrency("USD");  // Required field
            instrument.setTradable(true);
            instrumentRepository.save(instrument);
        }

        // Mock PositionMapper to return empty (no existing position)
        when(positionMapper.findByAccountIdAndSymbol(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        when(positionMapper.findByAccountId(anyLong()))
            .thenReturn(java.util.List.of());

        // Mock OrderMapper: track saved orders and return them on findByAccountId
        doAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            savedOrders.add(order);
            return null;
        }).when(orderMapper).save(any(Order.class));
        
        when(orderMapper.findByAccountId(anyLong()))
            .thenAnswer(invocation -> {
                long accountId = invocation.getArgument(0);
                return savedOrders.stream()
                    .filter(o -> o.getAccountId() == accountId)
                    .collect(java.util.stream.Collectors.toList());
            });

        // Mock accountMapper to properly convert entities to domain models
        when(accountMapper.toDomain(any(AccountEntity.class)))
            .thenAnswer(invocation -> {
                AccountEntity entity = invocation.getArgument(0);
                // Create a minimal Account domain model from the entity
               AccountStatusValidator mockValidator = Mockito.mock(AccountStatusValidator.class);
                return new Account(
                    entity.getId(),
                    entity.getHolderName(),
                    new Money(entity.getCashBalance()),
                    entity.getStatus(),
                    0,  // version
                    LocalDateTime.now(),
                    mockValidator
                );
            });
        
        // Mock instrumentMapper to properly convert entities to domain models
        lenient().when(instrumentMapper.toDomain(any(InstrumentEntity.class)))
            .thenAnswer(invocation -> {
                InstrumentEntity entity = invocation.getArgument(0);
                InstrumentSymbolValidator mockSymbolValidator = Mockito.mock(InstrumentSymbolValidator.class);
                return new Instrument(
                    entity.getSymbol(),  // Use symbol as id
                    entity.getSymbol(),
                    entity.getName(),
                    entity.getAssetClass(),
                    entity.getTradable(),
                    mockSymbolValidator
                );
            });
    }

    /**
     * Characterization: Successful BUY order returns FILLED status
     * 
     * Documents current behavior: When a BUY order is placed with sufficient funds,
     * it should be FILLED (not REJECTED, not remain NEW).
     */
    @Test
    @DisplayName("BUY with sufficient funds → status is FILLED")
    void char_buy_sufficient_funds_returns_filled() {
        PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
            TEST_ACCOUNT_ID,
            TEST_SYMBOL,
            OrderSide.BUY,
            10L,
            new BigDecimal("50.00"),
            "char-test-buy-001"
        );

        Order result = orderService.placeOrder(request);

        assertEquals(OrderStatus.FILLED, result.getStatus(), 
            "BUY order with sufficient funds should be FILLED");
    }

    /**
     * Characterization: Insufficient funds results in REJECTED status
     * 
     * Documents current behavior: Insufficient funds should result in REJECTED status,
     * not throw an exception.
     */
    @Test
    @DisplayName("BUY insufficient funds → status is REJECTED")
    void char_buy_insufficient_funds_returns_rejected() {
        PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
            TEST_ACCOUNT_ID,
            TEST_SYMBOL,
            OrderSide.BUY,
            1000000L,  // Huge quantity that exceeds balance
            new BigDecimal("100.00"),
            "char-test-insufficient-001"
        );

        Order result = orderService.placeOrder(request);

        assertEquals(OrderStatus.REJECTED, result.getStatus(),
            "BUY with insufficient funds should be REJECTED, not throw exception");
    }

    /**
     * Characterization: Non-existent account throws AccountNotFoundException
     * 
     * Documents current behavior: Missing account should throw, not return REJECTED.
     */
    @Test
    @DisplayName("Non-existent account → AccountNotFoundException thrown")
    void char_account_not_found_throws_exception() {
        PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
            999999L,  // Non-existent
            TEST_SYMBOL,
            OrderSide.BUY,
            10L,
            new BigDecimal("50.00"),
            "char-test-not-found-001"
        );

        assertThrows(AccountNotFoundException.class, () -> {
            orderService.placeOrder(request);
        });
    }

    /**
     * Characterization: Non-existent instrument throws InstrumentNotFoundException
     * 
     * Documents current behavior: Missing instrument should throw, not return REJECTED.
     */
    @Test
    @DisplayName("Non-existent instrument → InstrumentNotFoundException thrown")
    void char_instrument_not_found_throws_exception() {
        PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
            TEST_ACCOUNT_ID,
            "NONEXISTENT_SYMBOL",
            OrderSide.BUY,
            10L,
            new BigDecimal("50.00"),
            "char-test-symbol-not-found-001"
        );

        assertThrows(InstrumentNotFoundException.class, () -> {
            orderService.placeOrder(request);
        });
    }

    /**
     * Characterization: Duplicate idempotency key throws DuplicateOrderException
     * 
     * Documents current behavior: Same idempotency key should throw on second attempt.
     */
    @Test
    @DisplayName("Duplicate idempotency key → DuplicateOrderException thrown")
    void char_duplicate_idempotency_key_throws_exception() {
        String sameKey = "char-test-duplicate-001";
        PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
            TEST_ACCOUNT_ID,
            TEST_SYMBOL,
            OrderSide.BUY,
            10L,
            new BigDecimal("50.00"),
            sameKey
        );

        // First call succeeds
        Order first = orderService.placeOrder(request);
        assertEquals(OrderStatus.FILLED, first.getStatus());

        // Second call with same key throws
        assertThrows(DuplicateOrderException.class, () -> {
            orderService.placeOrder(request);
        });
    }

    /**
     * Characterization: Inactive account results in REJECTED status
     * 
     * Documents current behavior: SUSPENDED account should result in REJECTED order,
     * not throw exception.
     */
    @Test
    @DisplayName("Inactive account → status is REJECTED")
    void char_inactive_account_returns_rejected() {
        // Deactivate account
        AccountEntity account = accountRepository.findById(TEST_ACCOUNT_ID).orElseThrow();
        account.setStatus(AccountStatus.SUSPENDED);
        accountRepository.save(account);

        PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
            TEST_ACCOUNT_ID,
            TEST_SYMBOL,
            OrderSide.BUY,
            10L,
            new BigDecimal("50.00"),
            "char-test-inactive-001"
        );

        Order result = orderService.placeOrder(request);

        assertEquals(OrderStatus.REJECTED, result.getStatus(),
            "Order from inactive account should be REJECTED");
    }

    /**
     * Characterization: Order has correct account and symbol
     * 
     * Documents current behavior: Returned order should reflect request parameters.
     */
    @Test
    @DisplayName("Order preserves account and symbol from request")
    void char_order_preserves_request_fields() {
        // Reactivate account
        AccountEntity account = accountRepository.findById(TEST_ACCOUNT_ID).orElseThrow();
        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);

        PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
            TEST_ACCOUNT_ID,
            TEST_SYMBOL,
            OrderSide.BUY,
            50L,
            new BigDecimal("25.00"),
            "char-test-fields-001"
        );

        Order result = orderService.placeOrder(request);

        assertEquals(TEST_ACCOUNT_ID, result.getAccountId(),
            "Order should reflect request account ID");
        assertEquals(TEST_SYMBOL, result.getSymbol());
        assertEquals(OrderSide.BUY, result.getSide());
        assertEquals(50L, result.getQuantity());
        assertEquals(new BigDecimal("25.00"), result.getPrice());
    }
}
