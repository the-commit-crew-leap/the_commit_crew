package com.thecommitcrew.controller;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import com.thecommitcrew.domain.dto.InstrumentResponseDTO;
import com.thecommitcrew.domain.enums.AssetClass;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.service.InstrumentService;

@WebMvcTest(InstrumentController.class)
@Import(InstrumentControllerTest.TestSecurityConfig.class)
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class InstrumentControllerTest {

    private static final String TEST_SYMBOL = "AAPL";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InstrumentService instrumentService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private InstrumentResponseDTO instrument;

    @BeforeEach
    void setUp() {
        instrument = new InstrumentResponseDTO(
            TEST_SYMBOL,
            "Apple Inc.",
            AssetClass.EQUITY,
            "USD",
            true
        );
    }

    @Nested
    @DisplayName("GET /instruments")
    class GetInstrumentsEndpoint {
        @BeforeEach
        void setup() {
            reset(instrumentService);
        }

        @Test
        @DisplayName("Returns instruments successfully")
        void returnsInstrumentsSuccessfully() throws Exception {
            when(instrumentService.getInstruments()).thenReturn(List.of(instrument));

            mockMvc.perform(get("/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value(TEST_SYMBOL))
                .andExpect(jsonPath("$[0].name").value("Apple Inc."))
                .andExpect(jsonPath("$[0].assetClass").value("EQUITY"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].tradable").value(true));
        }

        @Test
        @DisplayName("Returns empty list when no instruments exist")
        void returnsEmptyListWhenNoInstrumentsExist() throws Exception {
            when(instrumentService.getInstruments()).thenReturn(List.of());

            mockMvc.perform(get("/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.empty()));
        }
    }

    @Nested
    @DisplayName("GET /instruments/{symbol}")
    class GetInstrumentEndpoint {
        @BeforeEach
        void setup() {
            reset(instrumentService);
        }

        @Test
        @DisplayName("Returns instrument successfully when found")
        void returnsInstrumentWhenFound() throws Exception {
            when(instrumentService.getInstrument(TEST_SYMBOL)).thenReturn(instrument);

            mockMvc.perform(get("/instruments/{symbol}", TEST_SYMBOL))
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

            mockMvc.perform(get("/instruments/{symbol}", TEST_SYMBOL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INSTRUMENT_NOT_FOUND"));
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