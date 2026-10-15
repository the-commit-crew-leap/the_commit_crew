package com.thecommitcrew.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.thecommitcrew.domain.model.Money;

public record TransactionResponseDTO(
    String accountId,
    String transactionType,
    BigDecimal amount,
    Money newBalance,
    LocalDateTime timestamp
) {}