package com.thecommitcrew.domain.dto;

import com.thecommitcrew.domain.model.Money;

public record BalanceResponseDTO (

    Long accountId,
    Money cashBalance

) {}
