package com.thecommitcrew.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thecommitcrew.config.AlpacaProperties;
import com.thecommitcrew.domain.dto.IntradayPriceResponse;

@Component
public class AlpacaMarketDataClient {
    private final AlpacaProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AlpacaMarketDataClient(
        AlpacaProperties properties,
        RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
            .baseUrl(properties.dataUrl())
            .defaultHeaders(headers -> {
                headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                headers.set("APCA-API-KEY-ID", properties.apiKey());
                headers.set("APCA-API-SECRET-KEY", properties.apiSecret());
            })
            .build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public String feed() {
        return properties.feed();
    }

    public LatestTrade getLatestTrade(String symbol) {
        LatestTradeResponse response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v2/stocks/{symbol}/trades/latest")
                .queryParam("feed", properties.feed())
                .build(symbol))
            .retrieve()
            .body(LatestTradeResponse.class);

        if (response == null || response.trade() == null || response.trade().price() == null) {
            throw new IllegalStateException("Alpaca returned no latest trade for symbol: " + symbol);
        }

        return response.trade();
    }

    public IntradayPriceResponse getIntradayPrices(String symbol) {
        ZoneId eastern = ZoneId.of("America/New_York");
        ZonedDateTime now = ZonedDateTime.now(eastern);
        ZonedDateTime requestStart = now.minusDays(7).toLocalDate().atTime(9, 30).atZone(eastern);

        JsonNode response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v2/stocks/{symbol}/bars")
                .queryParam("timeframe", "1Min")
                .queryParam("start", requestStart.toInstant().toString())
                .queryParam("end", now.toInstant().toString())
                .queryParam("feed", properties.feed())
                .queryParam("sort", "asc")
                .queryParam("limit", 10000)
                .build(symbol))
            .retrieve()
            .body(JsonNode.class);

        JsonNode barsNode = response == null ? null : response.path("bars");
        if (barsNode != null && barsNode.isObject()) {
            barsNode = barsNode.path(symbol);
        }
        if (barsNode == null || !barsNode.isArray()) {
            throw new IllegalStateException("Alpaca returned no intraday bars for symbol: " + symbol);
        }

        List<IntradayPriceResponse.IntradayBar> allBars = new ArrayList<>();
        for (JsonNode bar : barsNode) {
            allBars.add(new IntradayPriceResponse.IntradayBar(
                Instant.parse(bar.path("t").asText()),
                decimalField(bar, "o"),
                decimalField(bar, "h"),
                decimalField(bar, "l"),
                decimalField(bar, "c"),
                bar.path("v").asLong()
            ));
        }

        LocalDate sessionDate = allBars.stream()
            .map(bar -> bar.timestamp().atZone(eastern))
            .filter(timestamp -> isRegularSessionTime(timestamp.toLocalTime()))
            .map(ZonedDateTime::toLocalDate)
            .max(LocalDate::compareTo)
            .orElse(now.toLocalDate());
        List<IntradayPriceResponse.IntradayBar> bars = allBars.stream()
            .filter(bar -> {
                ZonedDateTime timestamp = bar.timestamp().atZone(eastern);
                return timestamp.toLocalDate().equals(sessionDate)
                    && isRegularSessionTime(timestamp.toLocalTime());
            })
            .toList();

        Instant updatedAt = bars.isEmpty() ? null : bars.get(bars.size() - 1).timestamp();
        return new IntradayPriceResponse(symbol, sessionDate, "ALPACA_" + properties.feed().toUpperCase(), true, updatedAt, bars);
    }

    private boolean isRegularSessionTime(java.time.LocalTime time) {
        return !time.isBefore(LocalTime.of(9, 30)) && time.isBefore(LocalTime.of(16, 0));
    }

    public CryptoSnapshot getLatestCryptoSnapshot(String symbol) {
        String responseBody = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v1beta3/crypto/{feed}/snapshots")
                .queryParam("symbols", symbol)
                .build("us"))
            .retrieve()
            .body(String.class);

        JsonNode response;
        try {
            response = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Alpaca returned invalid JSON for symbol: " + symbol, exception);
        }

        JsonNode snapshot = findCryptoSnapshot(response, symbol);
        if (snapshot == null || snapshot.get("latestTrade") == null) {
            throw new IllegalStateException("Alpaca returned no crypto snapshot for symbol: " + symbol);
        }

        JsonNode latestTrade = snapshot.get("latestTrade");
        JsonNode price = latestTrade.get("p");
        if (price == null || !price.isNumber() || price.decimalValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Alpaca returned an invalid crypto price for symbol: " + symbol);
        }

        JsonNode previousBar = snapshot.get("prevDailyBar");
        BigDecimal previousClose = previousBar != null && previousBar.get("c") != null
            ? previousBar.get("c").decimalValue()
            : null;
        Instant timestamp = latestTrade.get("t") == null
            ? null
            : Instant.parse(latestTrade.get("t").asText());

        return new CryptoSnapshot(price.decimalValue(), previousClose, timestamp);
    }

    private JsonNode findCryptoSnapshot(JsonNode response, String symbol) {
        if (response == null) {
            return null;
        }

        JsonNode snapshot = response.get(symbol);
        if (snapshot != null) {
            return snapshot;
        }

        JsonNode snapshots = response.get("snapshots");
        if (snapshots != null) {
            snapshot = snapshots.get(symbol);
            if (snapshot != null) {
                return snapshot;
            }
        }

        String normalizedSymbol = symbol.replace("/", "").toUpperCase();
        JsonNode candidates = snapshots == null ? response : snapshots;
        for (var entry : candidates.properties()) {
            if (entry.getKey().replace("/", "").toUpperCase().equals(normalizedSymbol)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private BigDecimal decimalField(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber() || value.decimalValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Alpaca returned an invalid intraday bar");
        }
        return value.decimalValue();
    }

    private record LatestTradeResponse(@JsonProperty("trade") LatestTrade trade) {
    }

    public record LatestTrade(
        @JsonProperty("p") BigDecimal price,
        @JsonProperty("t") Instant timestamp
    ) {
    }

    public record CryptoSnapshot(
        BigDecimal price,
        BigDecimal previousClose,
        Instant timestamp
    ) {
    }
}
