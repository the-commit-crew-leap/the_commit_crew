package com.thecommitcrew.controller;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import com.thecommitcrew.domain.dto.InstrumentResponseDTO;
import com.thecommitcrew.domain.enums.AssetClass;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.service.WatchlistService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebMvcTest(WatchlistController.class)
@Import(WatchlistControllerTest.TestSecurityConfig.class)
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class WatchlistControllerTest {

    private static final String TEST_ACCOUNT_ID = "ACC-1001";
    private static final String WATCHLIST_URL = "/api/v1/accounts/{id}/watchlist";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WatchlistService watchlistService;

    private InstrumentResponseDTO apple;

    @BeforeEach
    void setUp() {
        apple = new InstrumentResponseDTO(
            "AAPL",
            "Apple Inc.",
            AssetClass.EQUITY,
            "USD",
            true
        );
    }

    @Nested
    @DisplayName("GET /api/v1/accounts/{id}/watchlist")
    class GetWatchlistEndpoint {
        @BeforeEach
        void setup() {
            reset(watchlistService);
        }

        @Test
        @DisplayName("Returns the authenticated account watchlist")
        void returnsWatchlist() throws Exception {
            when(watchlistService.getWatchlist(TEST_ACCOUNT_ID))
                .thenReturn(List.of(apple));

            mockMvc.perform(get(WATCHLIST_URL, TEST_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].name").value("Apple Inc."))
                .andExpect(jsonPath("$[0].assetClass").value("EQUITY"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].tradable").value(true));
        }

        @Test
        @DisplayName("Returns an empty list when the account has no watchlist entries")
        void returnsEmptyWatchlist() throws Exception {
            when(watchlistService.getWatchlist(TEST_ACCOUNT_ID))
                .thenReturn(List.of());

            mockMvc.perform(get(WATCHLIST_URL, TEST_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.empty()));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/accounts/{id}/watchlist")
    class AddToWatchlistEndpoint {
        @BeforeEach
        void setup() {
            reset(watchlistService);
        }

        @Test
        @DisplayName("Adds an instrument to the authenticated account watchlist")
        void addsInstrument() throws Exception {
            when(watchlistService.addToWatchlist(TEST_ACCOUNT_ID, "AAPL"))
                .thenReturn(apple);

            mockMvc.perform(post(WATCHLIST_URL, TEST_ACCOUNT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"symbol\":\"AAPL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.name").value("Apple Inc."));

            verify(watchlistService).addToWatchlist(TEST_ACCOUNT_ID, "AAPL");
        }

        @Test
        @DisplayName("Returns 400 when the symbol is blank")
        void rejectsBlankSymbol() throws Exception {
            mockMvc.perform(post(WATCHLIST_URL, TEST_ACCOUNT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"symbol\":\"\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 404 when the instrument does not exist")
        void returns404WhenInstrumentDoesNotExist() throws Exception {
            when(watchlistService.addToWatchlist(TEST_ACCOUNT_ID, "UNKNOWN"))
                .thenThrow(new InstrumentNotFoundException("Instrument not found for symbol: UNKNOWN"));

            mockMvc.perform(post(WATCHLIST_URL, TEST_ACCOUNT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"symbol\":\"UNKNOWN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INSTRUMENT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/accounts/{id}/watchlist/{symbol}")
    class RemoveFromWatchlistEndpoint {
        @BeforeEach
        void setup() {
            reset(watchlistService);
        }

        @Test
        @DisplayName("Removes an instrument from the authenticated account watchlist")
        void removesInstrument() throws Exception {
            mockMvc.perform(delete(WATCHLIST_URL + "/{symbol}", TEST_ACCOUNT_ID, "AAPL"))
                .andExpect(status().isNoContent());

            verify(watchlistService).removeFromWatchlist(TEST_ACCOUNT_ID, "AAPL");
        }
    }

    @Test
    @DisplayName("Rejects access to another account watchlist")
    void rejectsAnotherAccount() throws Exception {
        mockMvc.perform(get(WATCHLIST_URL, "ACC-2002"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

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
                protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain
                ) throws ServletException, IOException {
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
