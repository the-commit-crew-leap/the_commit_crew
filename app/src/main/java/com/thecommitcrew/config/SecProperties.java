package com.thecommitcrew.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sec")
public record SecProperties(
    boolean enabled,
    String dataUrl,
    String userAgent
) {
    public SecProperties {
        dataUrl = dataUrl == null || dataUrl.isBlank() ? "https://data.sec.gov" : dataUrl;
        userAgent = userAgent == null ? "" : userAgent;
    }

    public boolean isConfigured() {
        return enabled && !userAgent.isBlank();
    }
}
