package com.thecommitcrew.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.thecommitcrew.auth.JwtTokenProvider;
import com.thecommitcrew.domain.dto.PlaceOrderRequestDTO;
import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.domain.exception.AccountNotFoundException;
import com.thecommitcrew.domain.exception.AccountNotActiveException;
import com.thecommitcrew.domain.exception.InsufficientFundsException;
import com.thecommitcrew.domain.exception.InsufficientHoldingsException;
import com.thecommitcrew.domain.exception.DuplicateOrderException;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.service.OrderService;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


/**
 * Test suite for OrderController.
 * Tests verify proper delegation to OrderService and exception handling.
 */
@WebMvcTest(OrderController.class)
@Import(OrderControllerTest.TestSecurityConfig.class)
@DisplayName("OrderController Tests")
@SuppressWarnings("null")
class OrderControllerTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final String SYMBOL = "AAPL";
    private static final long QUANTITY = 100L;
    private static final String PRICE = "150.00";
    private static final String IDEMPOTENCY_KEY = "idem-001";

    private PlaceOrderRequestDTO request;

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private OrderService orderService;

    @MockBean 
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        request = new PlaceOrderRequestDTO(
            ACCOUNT_ID,
            SYMBOL,
            OrderSide.BUY,
            QUANTITY,
            new BigDecimal(PRICE),
            IDEMPOTENCY_KEY
        );
    }

    /**
     * Test successfully placing an order that is filled.
     * OrderService.placeOrder returns a FILLED order.
     */
    @Test
    @DisplayName("POST /orders - Successfully place order (201 Created)")
    void testSubmitOrderSuccess() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order filledOrder = new Order(
            orderId,
            ACCOUNT_ID,
            SYMBOL,
            OrderSide.BUY,
            QUANTITY,
            new BigDecimal(PRICE),
            OrderStatus.FILLED,
            LocalDateTime.now(),
            IDEMPOTENCY_KEY
        );
        
        when(orderService.placeOrder(any(PlaceOrderRequestDTO.class)))
            .thenReturn(filledOrder);
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value(orderId.toString()))
            .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
            .andExpect(jsonPath("$.symbol").value(SYMBOL))
            .andExpect(jsonPath("$.side").value("BUY"))
            .andExpect(jsonPath("$.quantity").value(QUANTITY))
            .andExpect(jsonPath("$.price").value(Double.parseDouble(PRICE)))
            .andExpect(jsonPath("$.status").value("FILLED"));
    }
    
    /**
     * Test account not found exception handling.
     * OrderService throws AccountNotFoundException.
     */
    @Test
    @DisplayName("POST /orders - Account not found (404 via ControllerAdvice)")
    void testSubmitOrderAccountNotFound() throws Exception {
        when(orderService.placeOrder(any(PlaceOrderRequestDTO.class)))
            .thenThrow(new AccountNotFoundException("Account not found: " + ACCOUNT_ID));
        
        // Act & Assert - Exception is caught by @ControllerAdvice
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test instrument not found exception handling.
     */
    @Test
    @DisplayName("POST /orders - Instrument not found (404 via ControllerAdvice)")
    void testSubmitOrderInstrumentNotFound() throws Exception {
        when(orderService.placeOrder(any(PlaceOrderRequestDTO.class)))
            .thenThrow(new InstrumentNotFoundException("Instrument not found for symbol: " + SYMBOL));
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test account not active exception handling.
     */
    @Test
    @DisplayName("POST /orders - Account not active (422 Unprocessable Entity)")
    void testSubmitOrderAccountNotActive() throws Exception {
        when(orderService.placeOrder(any(PlaceOrderRequestDTO.class)))
            .thenThrow(new AccountNotActiveException("Account is not active"));
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity());
    }
    
    /**
     * Test insufficient funds exception handling.
     */
    @Test
    @DisplayName("POST /orders - Insufficient funds (422 Unprocessable Entity)")
    void testSubmitOrderInsufficientFunds() throws Exception {
        when(orderService.placeOrder(any(PlaceOrderRequestDTO.class)))
            .thenThrow(new InsufficientFundsException("Insufficient funds to buy 100 shares of " + SYMBOL));
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity());
    }
    
    /**
     * Test insufficient holdings exception handling.
     */
    @Test
    @DisplayName("POST /orders - Insufficient holdings (422 Unprocessable Entity)")
    void testSubmitOrderInsufficientHoldings() throws Exception {
        PlaceOrderRequestDTO sellRequest = new PlaceOrderRequestDTO(
            ACCOUNT_ID,
            SYMBOL,
            OrderSide.SELL,
            QUANTITY,
            new BigDecimal(PRICE),
            IDEMPOTENCY_KEY
        );
        
        when(orderService.placeOrder(any(PlaceOrderRequestDTO.class)))
            .thenThrow(new InsufficientHoldingsException("Insufficient holdings to sell 100 shares of " + SYMBOL));
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sellRequest)))
            .andExpect(status().isUnprocessableEntity());
    }
    
    /**
     * Test duplicate order exception handling.
     */
    @Test
    @DisplayName("POST /orders - Duplicate idempotency key (422 Unprocessable Entity)")
    void testSubmitOrderDuplicateIdempotency() throws Exception {
        when(orderService.placeOrder(any(PlaceOrderRequestDTO.class)))
            .thenThrow(new DuplicateOrderException("Order already exists with idempotency key: " + IDEMPOTENCY_KEY));
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity());
    }
    
    /**
     * Test invalid request body (validation failure).
     * Jakarta validation will reject this before the controller is invoked.
     */
    @Test
    @DisplayName("POST /orders - Invalid request body (400 Bad Request)")
    void testSubmitOrderInvalidRequest() throws Exception {
        // Arrange - Missing required fields
        String invalidRequest = "{\"symbol\": \"AAPL\"}";
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            .andExpect(status().is4xxClientError());
    }
    
    /**
     * Test successfully cancelling an order.
     * OrderService.cancelOrder completes without exception.
     */
    @Test
    @DisplayName("DELETE /orders/{id} - Successfully cancel order (204 No Content)")
    void testCancelOrderSuccess() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        
        // Act & Assert
        mockMvc.perform(delete("/api/v1/orders/" + orderId))
            .andExpect(status().isNoContent());
    }
    
    /**
     * Test invalid UUID format in path parameter.
     * UUID.fromString throws IllegalArgumentException, caught by Spring.
     */
    @Test
    @DisplayName("DELETE /orders/{id} - Invalid UUID format (400 Bad Request)")
    void testCancelOrderInvalidUUID() throws Exception {
        // Arrange
        String invalidUUID = "invalid-uuid-format";
        
        // Act & Assert
        mockMvc.perform(delete("/api/v1/orders/" + invalidUUID))
            .andExpect(status().isBadRequest());
    }
    
    /**
     * Test order not found exception handling during cancellation.
     * OrderService throws IllegalArgumentException when order not found.
     */
    @Test
    @DisplayName("DELETE /orders/{id} - Order not found (400 via ControllerAdvice)")
    void testCancelOrderNotFound() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Order not found: " + orderId))
            .when(orderService).cancelOrder(orderId);
        
        // Act & Assert
        mockMvc.perform(delete("/api/v1/orders/" + orderId))
            .andExpect(status().isBadRequest());
    }
    
    /**
     * Test order state error when cancelling non-NEW order.
     * OrderService throws IllegalStateException when order status is not NEW.
     */
    @Test
    @DisplayName("DELETE /orders/{id} - Order not in NEW status (400 via ControllerAdvice)")
    void testCancelOrderInvalidState() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        doThrow(new IllegalStateException("Only NEW orders can be cancelled"))
            .when(orderService).cancelOrder(orderId);
        
        // Act & Assert
        mockMvc.perform(delete("/api/v1/orders/" + orderId))
            .andExpect(status().isBadRequest());
    }

    /**
     * Test-only security configuration that disables authentication for all requests.
     */
    @TestConfiguration
    @EnableWebSecurity
    public static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()
                );
            return http.build();
        }
    }
}