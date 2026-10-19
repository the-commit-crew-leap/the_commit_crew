package com.thecommitcrew.service;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thecommitcrew.config.AlpacaProperties;

@Component
public class AlpacaMarketDataClient {
    private final AlpacaProperties properties;
    private final RestClient restClient;

    public AlpacaMarketDataClient(AlpacaProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
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

    public CryptoSnapshot getLatestCryptoSnapshot(String symbol) {
        JsonNode response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v1beta3/crypto/{feed}/snapshots")
                .queryParam("symbols", symbol)
                .build("us"))
            .retrieve()
            .body(JsonNode.class);

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
        var fields = candidates.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (entry.getKey().replace("/", "").toUpperCase().equals(normalizedSymbol)) {
                return entry.getValue();
            }
        }
        return null;
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
