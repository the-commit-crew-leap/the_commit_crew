package com.thecommitcrew.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import com.thecommitcrew.auth.AuthServiceClient;
import com.thecommitcrew.auth.JwtAuthenticationFilter;
import com.thecommitcrew.domain.enums.AccountStatus;
import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;
import com.thecommitcrew.domain.exception.AccountNotFoundException;
import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Money;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.domain.model.Position;
import com.thecommitcrew.service.AccountService;
import com.thecommitcrew.service.PositionService;
import com.thecommitcrew.service.PriceService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@WebMvcTest(AccountController.class)
@Import(AccountControllerTest.TestSecurityConfig.class)
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private PriceService priceService;

    @MockBean
    private PositionService positionService;

    private static final String TEST_ACCOUNT_ID = "ACC-1001";
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
            reset(accountService, priceService, positionService);
        }

        @Test
        @DisplayName("Returns positions with price and PnL data when account found")
        void returnsPositionsWithPriceAndPnLWhenAccountFound() throws Exception {
            Position position = new Position(TEST_ACCOUNT_ID, "AAPL", 100L, new BigDecimal("150.00"));
            List<Position> positions = List.of(position);

            BigDecimal currentPrice = new BigDecimal("195.50");
            BigDecimal marketValue = new BigDecimal("19550.00");
            BigDecimal unrealizedPnL = new BigDecimal("4550.00");
            BigDecimal unrealizedPnLPercent = new BigDecimal("30.3333");

            when(accountService.getPositions(TEST_ACCOUNT_ID)).thenReturn(positions);
            when(priceService.getCurrentPrice("AAPL")).thenReturn(currentPrice);
            when(positionService.marketValue(position, currentPrice)).thenReturn(marketValue);
            when(positionService.unrealizedProfitLoss(position, currentPrice)).thenReturn(unrealizedPnL);
            when(positionService.unrealizedPnLPercent(position, currentPrice)).thenReturn(unrealizedPnLPercent);

            mockMvc.perform(get("/accounts/{id}/positions", TEST_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].quantity").value(100))
                .andExpect(jsonPath("$[0].averageCost").value("150.0"))
                .andExpect(jsonPath("$[0].currentPrice").value("195.5"))
                .andExpect(jsonPath("$[0].marketValue").value("19550.0"))
                .andExpect(jsonPath("$[0].unrealizedPnL").value("4550.0"))
                .andExpect(jsonPath("$[0].unrealizedPnLPercent").value("30.3333"));
        }

        @Test
        @DisplayName("Returns 404 when price not found for symbol")
        void returns404WhenPriceNotFound() throws Exception {
            Position position = new Position(TEST_ACCOUNT_ID, "UNKNOWN", 100L, new BigDecimal("150.00"));
            List<Position> positions = List.of(position);

            when(accountService.getPositions(TEST_ACCOUNT_ID)).thenReturn(positions);
            when(priceService.getCurrentPrice("UNKNOWN"))
                .thenThrow(new com.thecommitcrew.domain.exception.PriceNotFoundException("No price found for symbol: UNKNOWN"));

            mockMvc.perform(get("/accounts/{id}/positions", TEST_ACCOUNT_ID))
                .andExpect(status().isNotFound());
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

    @Nested
    @DisplayName("POST /accounts/{id}/deposit")
    class DepositEndpoint {
        @BeforeEach
        void setup() {
            reset(accountService);
        }

        @Test
        @DisplayName("Successfully deposits money and returns updated balance")
        void successfullyDeposits() throws Exception {
            Money newBalance = new Money(new BigDecimal("11000.00"));
            Account updatedAccount = new Account(
                TEST_ACCOUNT_ID,
                TEST_ACCOUNT_HOLDER,
                newBalance,
                AccountStatus.ACTIVE,
                2,
                LocalDateTime.now(),
                new com.thecommitcrew.domain.validator.DefaultAccountStatusValidator()
            );

            when(accountService.deposit(TEST_ACCOUNT_ID, new Money(new BigDecimal("1000.00")))).thenReturn(updatedAccount);

            mockMvc.perform(post("/accounts/{id}/deposit", TEST_ACCOUNT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 1000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(TEST_ACCOUNT_ID))
                .andExpect(jsonPath("$.transactionType").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.newBalance.amount").value(11000.00));
        }

        @Test
        @DisplayName("Returns 400 when amount is not positive")
        void returns400WhenAmountNotPositive() throws Exception {
            mockMvc.perform(post("/accounts/{id}/deposit", TEST_ACCOUNT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": -100.00}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when amount is null")
        void returns400WhenAmountIsNull() throws Exception {
            mockMvc.perform(post("/accounts/{id}/deposit", TEST_ACCOUNT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": null}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 404 when account not found")
        void returns404WhenAccountNotFound() throws Exception {
            when(accountService.deposit(TEST_ACCOUNT_ID, new Money(new BigDecimal("1000.00"))))
                .thenThrow(new AccountNotFoundException("Account not found: " + TEST_ACCOUNT_ID));

            mockMvc.perform(post("/accounts/{id}/deposit", TEST_ACCOUNT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 1000.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("POST /accounts/{id}/withdraw")
    class WithdrawEndpoint {
        @BeforeEach
        void setup() {
            reset(accountService);
        }

        @Test
        @DisplayName("Successfully withdraws money and returns updated balance")
        void successfullyWithdraws() throws Exception {
            Money newBalance = new Money(new BigDecimal("9000.00"));
            Account updatedAccount = new Account(
                TEST_ACCOUNT_ID,
                TEST_ACCOUNT_HOLDER,
                newBalance,
                AccountStatus.ACTIVE,
                2,
                LocalDateTime.now(),
                new com.thecommitcrew.domain.validator.DefaultAccountStatusValidator()
            );

            when(accountService.withdraw(TEST_ACCOUNT_ID, new Money(new BigDecimal("1000.00")))).thenReturn(updatedAccount);

            mockMvc.perform(post("/accounts/{id}/withdraw", TEST_ACCOUNT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 1000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(TEST_ACCOUNT_ID))
                .andExpect(jsonPath("$.transactionType").value("WITHDRAW"))
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.newBalance.amount").value(9000.00));
        }

        @Test
        @DisplayName("Returns 400 when amount is not positive")
        void returns400WhenAmountNotPositive() throws Exception {
            mockMvc.perform(post("/accounts/{id}/withdraw", TEST_ACCOUNT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when amount is null")
        void returns400WhenAmountIsNull() throws Exception {
            mockMvc.perform(post("/accounts/{id}/withdraw", TEST_ACCOUNT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": null}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when insufficient funds")
        void returns400WhenInsufficientFunds() throws Exception {
            when(accountService.withdraw(TEST_ACCOUNT_ID, new Money(new BigDecimal("20000.00"))))
                .thenThrow(new IllegalArgumentException("Insufficient funds"));

            mockMvc.perform(post("/accounts/{id}/withdraw", TEST_ACCOUNT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 20000.00}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 404 when account not found")
        void returns404WhenAccountNotFound() throws Exception {
            when(accountService.withdraw(TEST_ACCOUNT_ID, new Money(new BigDecimal("1000.00"))))
                .thenThrow(new AccountNotFoundException("Account not found: " + TEST_ACCOUNT_ID));

            mockMvc.perform(post("/accounts/{id}/withdraw", TEST_ACCOUNT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 1000.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
        }
    }

    /**
     * Test-only security configuration that disables authentication for all requests.
     */
    @TestConfiguration
    @EnableWebSecurity
    public static class TestSecurityConfig {
        
        @Bean
        public AuthServiceClient authServiceClient() {
            return new AuthServiceClient(new RestTemplate());
        }
        
        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }
        
        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter(AuthServiceClient authServiceClient) {
            return new JwtAuthenticationFilter(authServiceClient) {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
                        throws ServletException, IOException {
                    request.setAttribute("username", "test-user");
                    request.setAttribute("accountId", TEST_ACCOUNT_ID);
                    filterChain.doFilter(request, response);
                }
            };
        }
        
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