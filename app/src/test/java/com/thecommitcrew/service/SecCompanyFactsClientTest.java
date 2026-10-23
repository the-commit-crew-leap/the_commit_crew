package com.thecommitcrew.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thecommitcrew.config.SecProperties;
import com.thecommitcrew.domain.dto.FundamentalsResponse;

class SecCompanyFactsClientTest {

    private static final String DATA_URL = "https://data.sec.test";
    private static final String USER_AGENT = "CommitCrew test@example.com";

    private MockRestServiceServer server;
    private SecCompanyFactsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new SecCompanyFactsClient(
            new SecProperties(true, DATA_URL, USER_AGENT),
            restClientBuilder,
            new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void getFundamentalsParsesLatestFactsAndCachesByNormalizedSymbol() {
        server.expect(requestTo("https://www.sec.gov/files/company_tickers.json"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("User-Agent", USER_AGENT))
            .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
            .andRespond(withSuccess("""
                {"0":{"cik_str":1234,"ticker":"ABC","title":"Example Corporation"}}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(DATA_URL + "/api/xbrl/companyfacts/CIK0000001234.json"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("User-Agent", USER_AGENT))
            .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
            .andRespond(withSuccess("""
                {
                  "entityName":"Example Corporation",
                  "facts":{
                    "us-gaap":{
                      "RevenueFromContractWithCustomerExcludingAssessedTax":{"units":{"USD":[
                        {"val":100,"filed":"2025-02-01","fp":"FY"},
                        {"val":125.5,"filed":"2026-02-01","fp":"FY"}
                      ]}},
                      "NetIncomeLoss":{"units":{"USD":[
                        {"val":25,"filed":"2026-02-02","fp":"FY"}
                      ]}},
                      "EarningsPerShareDiluted":{"units":{"USD/shares":[
                        {"val":2.5,"filed":"2026-02-03","fp":"FY"}
                      ]}},
                      "Assets":{"units":{"USD":[
                        {"val":500,"filed":"2026-02-04","fp":"FY"}
                      ]}},
                      "Liabilities":{"units":{"USD":[
                        {"val":200,"filed":"2026-02-05","fp":"FY"}
                      ]}},
                      "CashAndCashEquivalentsAtCarryingValue":{"units":{"USD":[
                        {"val":80,"filed":"2026-02-06","fp":"FY"}
                      ]}},
                      "NetCashProvidedByUsedInOperatingActivities":{"units":{"USD":[
                        {"val":40,"filed":"2026-02-07","fp":"FY"}
                      ]}}
                    },
                    "dei":{
                      "EntityCommonStockSharesOutstanding":{"units":{"shares":[
                        {"val":1000,"filed":"2026-02-08","fp":"FY"}
                      ]}}
                    }
                  }
                }
                """, MediaType.APPLICATION_JSON));

        FundamentalsResponse response = client.getFundamentals(" abc ");
        FundamentalsResponse cachedResponse = client.getFundamentals("ABC");

        assertSame(response, cachedResponse);
        assertEquals("ABC", response.symbol());
        assertEquals("Example Corporation", response.companyName());
        assertEquals(new BigDecimal("125.5"), response.revenue());
        assertEquals(new BigDecimal("25"), response.netIncome());
        assertEquals(new BigDecimal("2.5"), response.earningsPerShare());
        assertEquals(new BigDecimal("500"), response.totalAssets());
        assertEquals(new BigDecimal("200"), response.totalLiabilities());
        assertEquals(new BigDecimal("80"), response.cashAndEquivalents());
        assertEquals(new BigDecimal("40"), response.operatingCashFlow());
        assertEquals(new BigDecimal("1000"), response.sharesOutstanding());
        assertEquals(Instant.parse("2026-02-08T00:00:00Z"), response.filedAt());
        assertEquals("FY", response.fiscalPeriod());
    }

    @Test
    void getFundamentalsRejectsUnconfiguredClientBeforeMakingRequests() {
        client = new SecCompanyFactsClient(
            new SecProperties(false, DATA_URL, USER_AGENT),
            RestClient.builder(),
            new ObjectMapper()
        );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getFundamentals("ABC")
        );

        assertEquals("SEC EDGAR fundamentals are not configured", exception.getMessage());
    }

    @Test
    void getFundamentalsRejectsSymbolsWithoutCikMappings() {
        server.expect(requestTo("https://www.sec.gov/files/company_tickers.json"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getFundamentals("missing")
        );

        assertEquals("SEC CIK not found for symbol: MISSING", exception.getMessage());
    }

    @Test
    void getFundamentalsRejectsInvalidCompanyFactsJson() {
        expectTickerMapping();
        server.expect(requestTo(DATA_URL + "/api/xbrl/companyfacts/CIK0000001234.json"))
            .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getFundamentals("ABC")
        );

        assertEquals("SEC returned invalid Company Facts JSON for symbol: ABC", exception.getMessage());
    }

    @Test
    void getFundamentalsRejectsResponseWithoutUsGaapFacts() {
        expectTickerMapping();
        server.expect(requestTo(DATA_URL + "/api/xbrl/companyfacts/CIK0000001234.json"))
            .andRespond(withSuccess("{\"facts\":{}}", MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getFundamentals("ABC")
        );

        assertEquals("SEC returned no US-GAAP facts for symbol: ABC", exception.getMessage());
    }

    private void expectTickerMapping() {
        server.expect(requestTo("https://www.sec.gov/files/company_tickers.json"))
            .andRespond(withSuccess("""
                {"0":{"cik_str":1234,"ticker":"ABC"}}
                """, MediaType.APPLICATION_JSON));
    }
}