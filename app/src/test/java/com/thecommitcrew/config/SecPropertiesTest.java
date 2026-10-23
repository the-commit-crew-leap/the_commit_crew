package com.thecommitcrew.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecPropertiesTest {

    @Test
    void defaultsMissingDataUrlAndUserAgent() {
        SecProperties properties = new SecProperties(true, null, null);

        assertEquals("https://data.sec.gov", properties.dataUrl());
        assertEquals("", properties.userAgent());
        assertFalse(properties.isConfigured());
    }

    @Test
    void defaultsBlankDataUrlAndPreservesUserAgent() {
        SecProperties properties = new SecProperties(true, "  ", "CommitCrew contact@example.com");

        assertEquals("https://data.sec.gov", properties.dataUrl());
        assertEquals("CommitCrew contact@example.com", properties.userAgent());
        assertTrue(properties.isConfigured());
    }

    @Test
    void configurationRequiresBothEnabledAndNonblankUserAgent() {
        assertFalse(new SecProperties(false, "https://data.sec.gov", "contact@example.com").isConfigured());
        assertFalse(new SecProperties(true, "https://data.sec.gov", "  ").isConfigured());
    }
}