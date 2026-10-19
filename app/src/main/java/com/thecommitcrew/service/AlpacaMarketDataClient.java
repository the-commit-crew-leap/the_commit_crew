package com.thecommitcrew.service;

import java.math.BigDecimal;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    public BigDecimal getLatestTradePrice(String symbol) {
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

        return response.trade().price();
    }

    private record LatestTradeResponse(@JsonProperty("trade") Trade trade) {
    }

    private record Trade(@JsonProperty("p") BigDecimal price) {
    }
}
