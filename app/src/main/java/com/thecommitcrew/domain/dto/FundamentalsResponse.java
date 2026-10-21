package com.thecommitcrew.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FundamentalsResponse(
    String symbol,
    String companyName,
    BigDecimal revenue,
    BigDecimal netIncome,
    BigDecimal earningsPerShare,
    BigDecimal totalAssets,
    BigDecimal totalLiabilities,
    BigDecimal cashAndEquivalents,
    BigDecimal operatingCashFlow,
    BigDecimal sharesOutstanding,
    Instant filedAt,
    String fiscalPeriod
) {
}
