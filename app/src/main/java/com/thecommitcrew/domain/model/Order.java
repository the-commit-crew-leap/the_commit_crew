package com.thecommitcrew.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.enums.OrderStatus;

public class Order {
    private final UUID id;
    private final Long accountId;
    private final String symbol;
    private final OrderSide side;
    private final long quantity;
    private final BigDecimal price;
    private OrderStatus status;
    private final LocalDateTime createdOn;
    private final String idempotencyKey;

    public Order(UUID id, Long accountId, String symbol, OrderSide side, long quantity, BigDecimal price, OrderStatus status, LocalDateTime createdOn, String idempotencyKey) {
        this.id = validateNotNull(id, "Order ID cannot be null");
        this.accountId = validateNotNull(accountId, "Account ID cannot be null");
        this.symbol = validateNotBlank(symbol, "Symbol cannot be null or blank");
        this.side = validateNotNull(side, "Order side cannot be null");
        this.quantity = validatePositive(quantity, "Quantity must be positive");
        this.price = validatePriceNotNull(price, "Price cannot be null");
        this.status = validateNotNull(status, "Order status cannot be null");
        this.createdOn = validateNotNull(createdOn, "Created on cannot be null");
        this.idempotencyKey = validateNotBlank(idempotencyKey, "Idempotency key cannot be blank");
    }

    public UUID getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    private static <T> T validateNotNull(T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }

    private static String validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    private static long validatePositive(long value, String message) {
        if (value <= 0) throw new IllegalArgumentException(message);
        return value;
    }

    private static BigDecimal validatePriceNotNull(BigDecimal value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        return value;
    }

}