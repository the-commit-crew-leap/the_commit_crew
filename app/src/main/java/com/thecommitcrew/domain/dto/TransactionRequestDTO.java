package com.thecommitcrew.domain.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransactionRequestDTO(
    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be positive")
    BigDecimal amount
) {}