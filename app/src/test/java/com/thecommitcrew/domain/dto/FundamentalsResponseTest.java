package com.thecommitcrew.domain.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class FundamentalsResponseTest {

    @Test
    void storesAndExposesAllFundamentalsFields() {
        Instant filedAt = Instant.parse("2026-02-08T00:00:00Z");
        FundamentalsResponse response = new FundamentalsResponse(
            "ABC",
            "Example Corporation",
            new BigDecimal("125.50"),
            new BigDecimal("25.00"),
            new BigDecimal("2.50"),
            new BigDecimal("500"),
            new BigDecimal("200"),
            new BigDecimal("80"),
            new BigDecimal("40"),
            new BigDecimal("1000"),
            filedAt,
            "FY"
        );

        assertEquals("ABC", response.symbol());
        assertEquals("Example Corporation", response.companyName());
        assertEquals(new BigDecimal("125.50"), response.revenue());
        assertEquals(new BigDecimal("25.00"), response.netIncome());
        assertEquals(new BigDecimal("2.50"), response.earningsPerShare());
        assertEquals(new BigDecimal("500"), response.totalAssets());
        assertEquals(new BigDecimal("200"), response.totalLiabilities());
        assertEquals(new BigDecimal("80"), response.cashAndEquivalents());
        assertEquals(new BigDecimal("40"), response.operatingCashFlow());
        assertEquals(new BigDecimal("1000"), response.sharesOutstanding());
        assertEquals(filedAt, response.filedAt());
        assertEquals("FY", response.fiscalPeriod());
    }

    @Test
    void permitsUnavailableFacts() {
        FundamentalsResponse response = new FundamentalsResponse(
            "ABC", "Example Corporation", null, null, null, null, null, null, null, null, null, null
        );

        assertNull(response.revenue());
        assertNull(response.filedAt());
        assertNull(response.fiscalPeriod());
    }
}