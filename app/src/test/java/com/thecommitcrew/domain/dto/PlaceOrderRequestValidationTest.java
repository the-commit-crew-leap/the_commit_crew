package com.thecommitcrew.domain.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import com.thecommitcrew.domain.enums.OrderSide;

import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.Validation;
import jakarta.validation.ConstraintViolation;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

/*
* Using Validator instance since this class has annotations
* Validator checks that the validation rules work correctly
*/
public class PlaceOrderRequestValidationTest {
    
    private Validator validator;
    
    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    @Test
    void testValidPlaceOrderRequest() {
        PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
            1L,
            "AAPL",
            OrderSide.BUY,
            100L,
            new BigDecimal("150.00"),
            "KEY-12345"
        );
        
        Set<ConstraintViolation<PlaceOrderRequestDTO>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Nested
    @DisplayName("Symbol tests")
    class testingSymbol {

        @Test
        @DisplayName("Test null symbol")
        void testSymbolCannotBeNull() {
            PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
                1L,
                null,
                OrderSide.BUY,
                100L,
                new BigDecimal("150.00"),
                "KEY-12345"
            );
            
            Set<ConstraintViolation<PlaceOrderRequestDTO>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }

        @Test
        @DisplayName("Test empty symbol")
        void testSymbolCannotBeBlank() {
            PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
                1L,
                "   ",
                OrderSide.BUY,
                100L,
                new BigDecimal("150.00"),
                "KEY-12345"
            );
            
            Set<ConstraintViolation<PlaceOrderRequestDTO>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }
    }

    @Test
    @DisplayName("Test null side")
    void testSideCannotBeNull() {
        PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
            1L,
            "AAPL",
            null,
            100L,
            new BigDecimal("150.00"),
            "KEY-12345"
        );
        
        Set<ConstraintViolation<PlaceOrderRequestDTO>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Nested
    @DisplayName("Quantity tests")
    class testingQuantity {

        @Test
        @DisplayName("Test zero quantity")
        void testQuantityCannotBeZero() {
            PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
                1L,
                "AAPL",
                OrderSide.BUY,
                0L,
                new BigDecimal("150.00"),
                "KEY-12345"
            );
            
            Set<ConstraintViolation<PlaceOrderRequestDTO>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }
        
        @Test
        @DisplayName("Test negative quantity")
        void testQuantityMustBePositive() {
            PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
                1L,
                "AAPL",
                OrderSide.BUY,
                -5L,
                new BigDecimal("150.00"),
                "KEY-12345"
            );
            
            Set<ConstraintViolation<PlaceOrderRequestDTO>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Price tests")
    class testingPrice {

        @Test
        @DisplayName("Test null price")
        void testPriceCannotBeNull() {
            PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
                1L,
                "AAPL",
                OrderSide.BUY,
                100L,
                null,
                "KEY-12345"
            );
            
            Set<ConstraintViolation<PlaceOrderRequestDTO>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }
        
        @Test
        @DisplayName("Test zero price")
        void testPriceMustBePositive() {
            PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
                1L,
                "AAPL",
                OrderSide.BUY,
                100L,
                new BigDecimal("0.00"),
                "KEY-12345"
            );
            
            Set<ConstraintViolation<PlaceOrderRequestDTO>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Idempotency key tests")
    class testingIdempotencyKey {

        @Test
        @DisplayName("Test blank idempotency key")
        void testIdempotencyKeyCannotBeBlank() {
            PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
                1L,
                "AAPL",
                OrderSide.BUY,
                100L,
                new BigDecimal("150.00"),
                "   "
            );
            
            Set<ConstraintViolation<PlaceOrderRequestDTO>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }

        @Test
        @DisplayName("Test valid idempotency key")
        void testValidIdempotencyKey() {
            PlaceOrderRequestDTO request = new PlaceOrderRequestDTO(
                1L,
                "AAPL",
                OrderSide.BUY,
                100L,
                new BigDecimal("150.00"),
                "UNIQUE-KEY-123"
            );
            
            Set<ConstraintViolation<PlaceOrderRequestDTO>> violations = validator.validate(request);
            assertTrue(violations.isEmpty());
        }
    }
}
