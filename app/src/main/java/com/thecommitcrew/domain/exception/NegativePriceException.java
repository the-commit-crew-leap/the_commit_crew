package com.thecommitcrew.domain.exception;

/**
 * Thrown when a price is negative
 */
public class NegativePriceException extends RuntimeException {

    public NegativePriceException(String message) {
        super(message);
    }
}