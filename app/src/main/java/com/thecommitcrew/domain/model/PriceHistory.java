package com.thecommitcrew.domain.model;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class PriceHistory {
    private final UUID id;
    private final String symbol;
    private final LocalDate priceDate;
    private final BigDecimal openPrice;
    private final BigDecimal highPrice;
    private final BigDecimal lowPrice;
    private final BigDecimal closePrice;
    private final long volume;

    public PriceHistory(UUID id, String symbol, LocalDate priceDate, BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice, long volume) {
        this.id = validateNotNull(id, "ID cannot be null");
        this.symbol = validateNotBlank(symbol, "Symbol cannot be null or blank");
        this.priceDate = validateNotNull(priceDate, "Price date cannot be null");
        this.openPrice = validateNotNullAndNonNegative(openPrice, "Open price cannot be null or negative");
        this.highPrice = validateNotNullAndNonNegative(highPrice, "High price cannot be null or negative");
        this.lowPrice = validateNotNullAndNonNegative(lowPrice, "Low price cannot be null or negative");
        this.closePrice = validateNotNullAndNonNegative(closePrice, "Close price cannot be null or negative");
        this.volume = validateNonNegative(volume, "Volume cannot be negative");
    }

    private static <T> T validateNotNull(T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }

    private static String validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    private static BigDecimal validateNotNullAndNonNegative(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) 
            throw new IllegalArgumentException(message);
        return value;
    }

    private static long validateNonNegative(long value, String message) {
        if (value <= 0) throw new IllegalArgumentException(message);
        return value;
    }

    public UUID getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public long getVolume() {
        return volume;
    }

}