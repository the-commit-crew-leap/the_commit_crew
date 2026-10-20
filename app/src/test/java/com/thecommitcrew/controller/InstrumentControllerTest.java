package com.thecommitcrew.controller;
import com.thecommitcrew.auth.AuthServiceClient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import com.thecommitcrew.domain.dto.InstrumentResponseDTO;
import com.thecommitcrew.domain.dto.IntradayPriceResponse;
import com.thecommitcrew.domain.dto.LivePriceResponse;
import com.thecommitcrew.domain.enums.AssetClass;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.service.InstrumentService;
import com.thecommitcrew.service.MarketTickerService;
import com.thecommitcrew.service.PriceService;

@WebMvcTest(InstrumentController.class)
@Import(InstrumentControllerTest.TestSecurityConfig.class)
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class InstrumentControllerTest {

    private static final String TEST_SYMBOL = "AAPL";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstrumentService instrumentService;

    @MockitoBean
    private PriceService priceService;

    @MockitoBean
    private MarketTickerService marketTickerService;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    private InstrumentResponseDTO tradableInstrument;
    private InstrumentResponseDTO untradableInstrument;

    @BeforeEach
    void setUp() {
        tradableInstrument = new InstrumentResponseDTO(
            TEST_SYMBOL,
            "Apple Inc.",
            AssetClass.EQUITY,
            "USD",
            true
        );
        untradableInstrument = new InstrumentResponseDTO(
            "OLD_SYMBOL",
            "Old Company",
            AssetClass.EQUITY,
            "USD",
            false
        );
    }

    @Nested
    @DisplayName("GET /api/v1/instruments")
    class GetInstrumentsEndpoint {
        @BeforeEach
        void setup() {
            reset(instrumentService);
        }

        @Test
        @DisplayName("Returns all instruments when no filter applied")
        void returnsAllInstruments() throws Exception {
            when(instrumentService.getInstruments(null))
                .thenReturn(List.of(tradableInstrument, untradableInstrument));

            mockMvc.perform(get("/api/v1/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value(TEST_SYMBOL))
                .andExpect(jsonPath("$[0].tradable").value(true))
                .andExpect(jsonPath("$[1].symbol").value("OLD_SYMBOL"))
                .andExpect(jsonPath("$[1].tradable").value(false));
        }

        @Test
        @DisplayName("Returns empty list when no instruments exist")
        void returnsEmptyListWhenNoInstrumentsExist() throws Exception {
            when(instrumentService.getInstruments(null)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.empty()));
        }

        @Test
        @DisplayName("Returns only tradable instruments when tradable=true")
        void returnsTradableInstrumentsWhenFilteredByTradable() throws Exception {
            when(instrumentService.getInstruments(true))
                .thenReturn(List.of(tradableInstrument));

            mockMvc.perform(get("/api/v1/instruments?tradable=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value(TEST_SYMBOL))
                .andExpect(jsonPath("$[0].tradable").value(true));
        }

        @Test
        @DisplayName("Returns only untradable instruments when tradable=false")
        void returnsUntradableInstrumentsWhenFilteredByNotTradable() throws Exception {
            when(instrumentService.getInstruments(false))
                .thenReturn(List.of(untradableInstrument));

            mockMvc.perform(get("/api/v1/instruments?tradable=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("OLD_SYMBOL"))
                .andExpect(jsonPath("$[0].tradable").value(false));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/instruments/{symbol}")
    class GetInstrumentEndpoint {
        @BeforeEach
        void setup() {
            reset(instrumentService);
        }

        @Test
        @DisplayName("Returns instrument successfully when found")
        void returnsInstrumentWhenFound() throws Exception {
            when(instrumentService.getInstrument(TEST_SYMBOL)).thenReturn(tradableInstrument);

            mockMvc.perform(get("/api/v1/instruments/{symbol}", TEST_SYMBOL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value(TEST_SYMBOL))
                .andExpect(jsonPath("$.name").value("Apple Inc."))
                .andExpect(jsonPath("$.assetClass").value("EQUITY"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.tradable").value(true));
        }

        @Test
        @DisplayName("Returns 404 when instrument not found")
        void returns404WhenInstrumentNotFound() throws Exception {
            when(instrumentService.getInstrument(TEST_SYMBOL))
                .thenThrow(new InstrumentNotFoundException("Instrument not found for symbol: " + TEST_SYMBOL));

            mockMvc.perform(get("/api/v1/instruments/{symbol}", TEST_SYMBOL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INSTRUMENT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/instruments/{symbol}/price")
    class GetInstrumentPriceEndpoint {
        @BeforeEach
        void setup() {
            reset(priceService);
        }

        @Test
        @DisplayName("Returns the latest price for an instrument")
        void returnsCurrentPrice() throws Exception {
            when(priceService.getCurrentLivePrice(TEST_SYMBOL))
                .thenReturn(new LivePriceResponse(
                    TEST_SYMBOL,
                    new java.math.BigDecimal("195.50"),
                    "ALPACA_IEX",
                    true,
                    java.time.Instant.parse("2026-10-19T10:00:00Z")
                ));

            mockMvc.perform(get("/api/v1/instruments/{symbol}/price", "aapl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value(TEST_SYMBOL))
                .andExpect(jsonPath("$.price").value(195.50))
                .andExpect(jsonPath("$.source").value("ALPACA_IEX"))
                .andExpect(jsonPath("$.live").value(true))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-19T10:00:00Z"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/instruments/{symbol}/intraday")
    class GetInstrumentIntradayEndpoint {
        @Test
        @DisplayName("Returns normalized intraday bars")
        void returnsIntradayBars() throws Exception {
            when(priceService.getIntradayPrices(TEST_SYMBOL))
                .thenReturn(new IntradayPriceResponse(
                    TEST_SYMBOL,
                    LocalDate.of(2026, 10, 20),
                    "ALPACA_IEX",
                    true,
                    Instant.parse("2026-10-20T14:31:00Z"),
                    List.of(new IntradayPriceResponse.IntradayBar(
                        Instant.parse("2026-10-20T14:30:00Z"),
                        new BigDecimal("195.00"),
                        new BigDecimal("196.00"),
                        new BigDecimal("194.50"),
                        new BigDecimal("195.50"),
                        1000
                    ))
                ));

            mockMvc.perform(get("/api/v1/instruments/{symbol}/intraday", "aapl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value(TEST_SYMBOL))
                .andExpect(jsonPath("$.source").value("ALPACA_IEX"))
                .andExpect(jsonPath("$.bars[0].close").value(195.50));
        }
    }

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