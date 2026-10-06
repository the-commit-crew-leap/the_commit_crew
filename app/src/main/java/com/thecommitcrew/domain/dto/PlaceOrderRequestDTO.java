package com.thecommitcrew.domain.dto;

import java.math.BigDecimal;

import com.thecommitcrew.domain.enums.OrderSide;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;


public record PlaceOrderRequestDTO (

    @NotNull(message = "Account id cannot be null")
    @NotBlank(message = "Account id cannot be blank")
    String accountId,
    
    @NotBlank(message = "Symbol cannot be blank")
    String symbol,
    
    @NotNull(message = "Side cannot be null")
    OrderSide side,
    
    @NotNull(message = "Quantity cannot be null")
    @Positive(message = "Quantity must be positive")
    long quantity,
    
    @NotNull(message = "Price cannot be null")
    @Positive(message = "Price must be positive")
    BigDecimal price,
    
    @NotBlank(message = "Idempotency key cannot be blank")
    String idempotencyKey

) {}
