/**
 * Represents a financial position - a holding of a security within an account.
 */

package com.thecommitcrew.domain.model;
import java.math.BigDecimal;

public class Position {
    private final Long accountId;
    private final String symbol;
    private final long quantity;
    private final BigDecimal averageCost;

    public Position(Long accountId, String symbol, long quantity, BigDecimal averageCost) {
        this.accountId = validateNotNull(accountId, "Account ID cannot be null");
        this.symbol = validateNotBlank(symbol, "Symbol cannot be null or blank");
        this.quantity = validateNonNegative(quantity, "Quantity cannot be negative");
        this.averageCost = validateNotNull(averageCost, "Average cost cannot be null");
        
        if (averageCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Average cost cannot be negative");
        }
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    private static <T> T validateNotNull(T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }

    private static String validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    private static long validateNonNegative(long value, String message) {
        if (value < 0) throw new IllegalArgumentException(message);
        return value;
    }
}