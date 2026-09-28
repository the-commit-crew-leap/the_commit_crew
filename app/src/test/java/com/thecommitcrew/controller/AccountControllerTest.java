package com.thecommitcrew.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import com.thecommitcrew.auth.JwtTokenProvider;
import com.thecommitcrew.domain.enums.AccountStatus;
import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;
import com.thecommitcrew.domain.exception.AccountNotFoundException;
import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Money;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.domain.model.Position;
import com.thecommitcrew.service.AccountService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@WebMvcTest(AccountController.class)
@Import(AccountControllerTest.TestSecurityConfig.class)
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
public class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean 
    private JwtTokenProvider jwtTokenProvider;

    private static final Long TEST_ACCOUNT_ID = 1L;
    private static final String TEST_ACCOUNT_HOLDER = "John Doe";

    private Account testAccount;
    private Money testBalance;

    @BeforeEach
    void setUp() {
        testBalance = new Money(new BigDecimal("10000.00"));
        testAccount = new Account(
            TEST_ACCOUNT_ID,
            TEST_ACCOUNT_HOLDER,
            testBalance,
            AccountStatus.ACTIVE,
            1,
            LocalDateTime.now(),
            new com.thecommitcrew.domain.validator.DefaultAccountStatusValidator()
        );
    }

    @Nested
    @DisplayName("GET /accounts/{id}")
    class GetAccountEndpoint {
        @BeforeEach
        void setup() {
            reset(accountService);
        }

        @Test
        @DisplayName("Returns account successfully when found")
        void returnsAccountWhenFound() throws Exception {
            when(accountService.getAccount(TEST_ACCOUNT_ID)).thenReturn(testAccount);

            mockMvc.perform(get("/accounts/{id}", TEST_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(TEST_ACCOUNT_ID))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("Returns 404 when account not found")
        void returns404WhenAccountNotFound() throws Exception {
            when(accountService.getAccount(TEST_ACCOUNT_ID))
                .thenThrow(new AccountNotFoundException("Account not found: " + TEST_ACCOUNT_ID));

            mockMvc.perform(get("/accounts/{id}", TEST_ACCOUNT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /accounts/{id}/balance")
    class GetAccountBalanceEndpoint {
        @BeforeEach
        void setup() {
            reset(accountService);
        }

        @Test
        @DisplayName("Returns balance successfully when account found")
        void returnsBalanceWhenAccountFound() throws Exception {
            when(accountService.getBalance(TEST_ACCOUNT_ID)).thenReturn(testBalance);

            mockMvc.perform(get("/accounts/{id}/balance", TEST_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(TEST_ACCOUNT_ID))
                .andExpect(jsonPath("$.cashBalance.amount").value("10000.0"));

        }

        @Test
        @DisplayName("Returns 404 when account not found")
        void returns404WhenAccountNotFound() throws Exception {
            when(accountService.getBalance(TEST_ACCOUNT_ID))
                .thenThrow(new AccountNotFoundException("Account not found: " + TEST_ACCOUNT_ID));

            mockMvc.perform(get("/accounts/{id}/balance", TEST_ACCOUNT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /accounts/{id}/positions")
    class GetAccountPositionsEndpoint {
        @BeforeEach
        void setup() {
            reset(accountService);
        }

        @Test
        @DisplayName("Returns positions successfully when account found")
        void returnsPositionsWhenAccountFound() throws Exception {
            Position position = new Position(TEST_ACCOUNT_ID, "AAPL", 100L, new BigDecimal("150.00"));
            List<Position> positions = List.of(position);

            when(accountService.getPositions(TEST_ACCOUNT_ID)).thenReturn(positions);

            mockMvc.perform(get("/accounts/{id}/positions", TEST_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].quantity").value(100))
                .andExpect(jsonPath("$[0].averageCost").value("150.0"));
        }

        @Test
        @DisplayName("Returns 404 when account not found")
        void returns404WhenAccountNotFound() throws Exception {
            when(accountService.getPositions(TEST_ACCOUNT_ID))
                .thenThrow(new AccountNotFoundException("Account not found: " + TEST_ACCOUNT_ID));

            mockMvc.perform(get("/accounts/{id}/positions", TEST_ACCOUNT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
        }

        @Test
        @DisplayName("Returns empty list when no positions exist")
        void returnsEmptyListWhenNoPositionsExist() throws Exception {
            when(accountService.getPositions(TEST_ACCOUNT_ID)).thenReturn(List.of());

            mockMvc.perform(get("/accounts/{id}/positions", TEST_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.empty()));
        }
    }

    @Nested
    @DisplayName("GET /accounts/{id}/orders")
    class GetAccountOrdersEndpoint {
        @BeforeEach
        void setup() {
            reset(accountService);
        }

        @Test
        @DisplayName("Returns orders successfully when account found")
        void returnsOrdersWhenAccountFound() throws Exception {
            Order order = new Order(
                UUID.randomUUID(),
                TEST_ACCOUNT_ID,
                "AAPL",
                OrderSide.BUY,
                100L,
                new BigDecimal("150.0"),
                OrderStatus.FILLED,
                LocalDateTime.now(),
                "idempotency-key-123"
            );
            List<Order> orders = List.of(order);

            when(accountService.getOrders(TEST_ACCOUNT_ID)).thenReturn(orders);

            mockMvc.perform(get("/accounts/{id}/orders", TEST_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].quantity").value(100))
                .andExpect(jsonPath("$[0].status").value("FILLED"));
        }

        @Test
        @DisplayName("Returns 404 when account not found")
        void returns404WhenAccountNotFound() throws Exception {
            when(accountService.getOrders(TEST_ACCOUNT_ID))
                .thenThrow(new AccountNotFoundException("Account not found: " + TEST_ACCOUNT_ID));

            mockMvc.perform(get("/accounts/{id}/orders", TEST_ACCOUNT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
        }

        @Test
        @DisplayName("Returns empty list when no orders exist")
        void returnsEmptyListWhenNoOrdersExist() throws Exception {
            when(accountService.getOrders(TEST_ACCOUNT_ID)).thenReturn(List.of());

            mockMvc.perform(get("/accounts/{id}/orders", TEST_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.empty()));
        }
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