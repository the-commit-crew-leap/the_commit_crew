package com.thecommitcrew.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thecommitcrew.auth.JwtTokenProvider;
import com.thecommitcrew.domain.dto.ErrorResponseDTO;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@WebMvcTest(TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestSecurityConfig.class})
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean 
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @DisplayName("404 Not Found exceptions")
    class NotFoundExceptions {
        @Test
        @DisplayName("Handles AccountNotFoundException with 404 status")
        void handlesAccountNotFound() throws Exception {
            mockMvc.perform(get("/test-account-not-found"))
                .andExpect(result -> {
                    if (result.getResolvedException() != null) {
                        System.out.println("EXCEPTION: " + result.getResolvedException());
                        result.getResolvedException().printStackTrace();
                    }
                })
                .andDo(print())
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Handles InstrumentNotFoundException with 404 status")
        void handlesInstrumentNotFound() throws Exception {
            mockMvc.perform(get("/test-instrument-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("INSTRUMENT_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Instrument AAPL not found")));
        }
    }

    @Nested
    @DisplayName("422 Unprocessable Entity exceptions")
    class UnprocessableEntityExceptions {
        @Test
        @DisplayName("Handles AccountNotActiveException with 422 status")
        void handlesAccountNotActive() throws Exception {
            mockMvc.perform(get("/test-account-not-active"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("ACCOUNT_NOT_ACTIVE")))
                .andExpect(jsonPath("$.message", is("Account is not active")));
        }

        @Test
        @DisplayName("Handles InsufficientFundsException with 422 status")
        void handlesInsufficientFunds() throws Exception {
            mockMvc.perform(get("/test-insufficient-funds"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INSUFFICIENT_FUNDS")))
                .andExpect(jsonPath("$.message", is("Insufficient funds to complete transaction")));
        }

        @Test
        @DisplayName("Handles InsufficientHoldingsException with 422 status")
        void handlesInsufficientHoldings() throws Exception {
            mockMvc.perform(get("/test-insufficient-holdings"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INSUFFICIENT_HOLDINGS")))
                .andExpect(jsonPath("$.message", is("Insufficient holdings to sell")));
        }

        @Test
        @DisplayName("Handles DuplicateOrderException with 422 status")
        void handlesDuplicateOrder() throws Exception {
            mockMvc.perform(get("/test-duplicate-order"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("DUPLICATE_ORDER")))
                .andExpect(jsonPath("$.message", is("Order with ID 456 already exists")));
        }
    }

    @Nested
    @DisplayName("400 Bad Request exceptions")
    class BadRequestExceptions {
        @Test
        @DisplayName("Handles IllegalArgumentException with 400 status")
        void handlesIllegalArgument() throws Exception {
            mockMvc.perform(get("/test-illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_REQUEST")))
                .andExpect(jsonPath("$.message", is("Invalid argument provided")));
        }

        @Test
        @DisplayName("Handles NegativePriceException with 400 status")
        void handlesNegativePrice() throws Exception {
            mockMvc.perform(get("/test-negative-price"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_PRICE")))
                .andExpect(jsonPath("$.message", is("Price cannot be negative")));
        }
    }

    @Nested
    @DisplayName("500 Internal Server Error exceptions")
    class InternalServerErrorExceptions {
        @Test
        @DisplayName("Handles generic Exception with 500 status")
        void handlesGenericException() throws Exception {
            mockMvc.perform(get("/test-generic-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode", is("INTERNAL_SERVER_ERROR")))
                .andExpect(jsonPath("$.message", is("An unexpected error occurred")));
        }
    }

    @Nested
    @DisplayName("Response structure validation")
    class ResponseStructure {
        @Test
        @DisplayName("Response contains both errorCode and message fields")
        void responseContainsBothErrorCodeAndMessage() throws Exception {
            MvcResult result = mockMvc.perform(get("/test-account-not-found"))
                .andExpect(status().isNotFound())
                .andReturn();

            String content = result.getResponse().getContentAsString();
            ErrorResponseDTO response = objectMapper.readValue(content, ErrorResponseDTO.class);
            
            assert response != null;
            assert response.errorCode() != null;
            assert response.message() != null;
        }
    }

    @Test
    @DisplayName("Handles IllegalStateException with 400 status")
    void handlesIllegalState() throws Exception {
        mockMvc.perform(get("/test-illegal-state"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("INVALID_STATE")))
            .andExpect(jsonPath("$.message", is("Only NEW orders can be cancelled")));
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