package com.thecommitcrew.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alpaca")
public record AlpacaProperties(
    boolean enabled,
    String apiKey,
    String apiSecret,
    String dataUrl,
    String feed
) {
    public AlpacaProperties {
        apiKey = apiKey == null ? "" : apiKey;
        apiSecret = apiSecret == null ? "" : apiSecret;
        dataUrl = dataUrl == null || dataUrl.isBlank()
            ? "https://data.alpaca.markets"
            : dataUrl;
        feed = feed == null || feed.isBlank() ? "iex" : feed;
    }

    public boolean isConfigured() {
        return enabled && !apiKey.isBlank() && !apiSecret.isBlank();
    }
}
