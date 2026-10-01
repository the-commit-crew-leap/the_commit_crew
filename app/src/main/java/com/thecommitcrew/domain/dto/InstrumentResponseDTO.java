package com.thecommitcrew.domain.dto;

import com.thecommitcrew.domain.enums.AssetClass;

public record InstrumentResponseDTO(

    String symbol,
    String name,
    AssetClass assetClass,
    String currency,
    Boolean tradable

) {}