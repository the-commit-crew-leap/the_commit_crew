package com.thecommitcrew.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record WatchlistRequestDTO(
    @NotBlank(message = "symbol is required") String symbol
) {}
