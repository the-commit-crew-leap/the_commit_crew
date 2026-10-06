package com.thecommitcrew.domain.model;
import java.math.BigDecimal;
import java.util.Objects;

public class Money {
    private final BigDecimal amount;
    private static final String CURRENCY = "USD";

    /**
     * Constructs a new Money instance with the specified amount and currency.
     *
     * @param amount   the monetary amount, must be non-negative
     * @param CURRENCY the currency code, cannot be null or blank
     * @throws IllegalArgumentException if the amount is negative or the currency is null/blank
     */
    public Money(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        this.amount = amount;
    }

    /**
     * Adds the specified Money to this Money.
     *
     * @param other the Money to add, must have the same currency
     * @return a new Money instance representing the sum
     * @throws IllegalArgumentException if the currencies do not match
     */
    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    /**
     * Subtracts the specified Money from this Money.
     *
     * @param other the Money to subtract, must have the same currency and not exceed this amount
     * @return a new Money instance representing the difference
     * @throws IllegalArgumentException if the currencies do not match or if the other amount is greater than this amount
     */
    public Money subtract(Money other) {
        if (other.amount.compareTo(this.amount) > 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }
        return new Money(this.amount.subtract(other.amount));
    }

    /**
     * Checks if the monetary amount is positive.
     *
     * @return true if the amount is greater than zero, false otherwise
     */
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Returns the monetary amount.
     *
     * @return the amount as a BigDecimal
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Returns the currency code.
     *
     * @return the currency as a String
     */
    public String getCurrency() {
        return CURRENCY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return Objects.equals(amount, money.amount) && Objects.equals(CURRENCY, Money.CURRENCY);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, CURRENCY);
    }

    @Override
    public String toString() {
        return amount + " " + CURRENCY;
    }
}