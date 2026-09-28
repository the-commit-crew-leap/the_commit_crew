package com.thecommitcrew.domain.dto;

public record ErrorResponseDTO (

    String errorCode,
    String message

) {}