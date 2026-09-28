package com.thecommitcrew.domain.dto;

import com.thecommitcrew.domain.enums.AccountStatus;
import com.thecommitcrew.domain.model.Money;

public record AccountResponseDTO (

    Long accountId,
    AccountStatus status,
    Money cashBalance

) {}
