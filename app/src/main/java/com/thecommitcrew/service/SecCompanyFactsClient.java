package com.thecommitcrew.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thecommitcrew.config.SecProperties;
import com.thecommitcrew.domain.dto.FundamentalsResponse;

@Component
public class SecCompanyFactsClient {
    private static final long CACHE_MILLIS = 24 * 60 * 60 * 1000L;
    private final SecProperties properties;
    private final RestClient dataClient;
    private final RestClient secWebClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedFacts> factsCache = new ConcurrentHashMap<>();
    private volatile Map<String, String> tickerToCik;
    private volatile long tickerCacheTime;

    public SecCompanyFactsClient(
        SecProperties properties,
        RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.dataClient = restClientBuilder.clone()
            .baseUrl(properties.dataUrl())
            .defaultHeaders(this::setHeaders)
            .build();
        this.secWebClient = restClientBuilder.clone()
            .defaultHeaders(this::setHeaders)
            .build();
    }

    public FundamentalsResponse getFundamentals(String symbol) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("SEC EDGAR fundamentals are not configured");
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        CachedFacts cached = factsCache.get(normalizedSymbol);
        if (cached != null && System.currentTimeMillis() - cached.loadedAt() < CACHE_MILLIS) {
            return cached.response();
        }

        String cik = loadTickerToCik().get(normalizedSymbol);
        if (cik == null) {
            throw new IllegalStateException("SEC CIK not found for symbol: " + normalizedSymbol);
        }

        String responseBody = dataClient.get()
            .uri("/api/xbrl/companyfacts/CIK" + cik + ".json")
            .retrieve()
            .body(String.class);
        JsonNode response;
        try {
            response = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("SEC returned invalid Company Facts JSON for symbol: " + normalizedSymbol, exception);
        }
        FundamentalsResponse fundamentals = parse(normalizedSymbol, response);
        factsCache.put(normalizedSymbol, new CachedFacts(fundamentals, System.currentTimeMillis()));
        return fundamentals;
    }

    private synchronized Map<String, String> loadTickerToCik() {
        if (tickerToCik != null && System.currentTimeMillis() - tickerCacheTime < CACHE_MILLIS) {
            return tickerToCik;
        }

        String responseBody = secWebClient.get()
            .uri("https://www.sec.gov/files/company_tickers.json")
            .retrieve()
            .body(String.class);
        JsonNode response;
        try {
            response = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("SEC returned invalid ticker mapping JSON", exception);
        }
        Map<String, String> mapping = new HashMap<>();
            for (var entry : response.properties()) {
            JsonNode company = entry.getValue();
            String ticker = company.path("ticker").asText().toUpperCase();
            String cik = String.format("%010d", company.path("cik_str").asLong());
            mapping.put(ticker, cik);
            }
        tickerToCik = mapping;
        tickerCacheTime = System.currentTimeMillis();
        return mapping;
    }

    private FundamentalsResponse parse(String symbol, JsonNode response) {
        JsonNode facts = response == null ? null : response.path("facts").path("us-gaap");
        JsonNode dei = response == null ? null : response.path("facts").path("dei");
        if (facts == null || facts.isMissingNode()) {
            throw new IllegalStateException("SEC returned no US-GAAP facts for symbol: " + symbol);
        }

        FactValue revenue = find(facts, "USD", "RevenueFromContractWithCustomerExcludingAssessedTax", "Revenues", "SalesRevenueNet");
        FactValue netIncome = find(facts, "USD", "NetIncomeLoss");
        FactValue eps = find(facts, "USD/shares", "EarningsPerShareDiluted", "EarningsPerShareBasic");
        FactValue assets = find(facts, "USD", "Assets");
        FactValue liabilities = find(facts, "USD", "Liabilities");
        FactValue cash = find(facts, "USD", "CashAndCashEquivalentsAtCarryingValue");
        FactValue operatingCashFlow = find(facts, "USD", "NetCashProvidedByUsedInOperatingActivities");
        FactValue shares = find(dei, "shares", "EntityCommonStockSharesOutstanding");
        if (shares == null) {
            shares = find(facts, "shares", "WeightedAverageNumberOfSharesOutstandingBasic");
        }

        FactValue freshest = freshest(revenue, netIncome, eps, assets, liabilities, cash, operatingCashFlow, shares);
        return new FundamentalsResponse(
            symbol,
                response == null ? symbol : response.path("entityName").asText(symbol),
            value(revenue), value(netIncome), value(eps), value(assets), value(liabilities),
            value(cash), value(operatingCashFlow), value(shares),
            freshest == null ? null : freshest.filedAt(),
            freshest == null ? null : freshest.fiscalPeriod()
        );
    }

    private FactValue find(JsonNode facts, String unit, String... tags) {
        if (facts == null || facts.isMissingNode()) {
            return null;
        }
        for (String tag : tags) {
            JsonNode units = facts.path(tag).path("units").path(unit);
            if (!units.isArray() || units.isEmpty()) {
                continue;
            }
            return latest(units);
        }
        return null;
    }

    private FactValue latest(JsonNode units) {
        return java.util.stream.StreamSupport.stream(units.spliterator(), false)
            .filter(node -> node.path("val").isNumber() && !node.path("filed").isMissingNode())
            .map(node -> new FactValue(
                node.path("val").decimalValue(),
                LocalDate.parse(node.path("filed").asText()).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                node.path("fp").asText(null)
            ))
                .max((left, right) -> left.filedAt().compareTo(right.filedAt()))
            .orElse(null);
    }

    @SuppressWarnings("null")
    private FactValue freshest(FactValue... values) {
        return Arrays.stream(values)
            .filter(Objects::nonNull)
            .max(Comparator.comparing(FactValue::filedAt))
            .orElse(null);
    }

    private BigDecimal value(FactValue fact) {
        return fact == null ? null : fact.value();
    }

    private void setHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.USER_AGENT, properties.userAgent());
    }

    private record FactValue(BigDecimal value, Instant filedAt, String fiscalPeriod) {
    }

    private record CachedFacts(FundamentalsResponse response, long loadedAt) {
    }
}
