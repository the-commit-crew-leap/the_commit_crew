package com.thecommitcrew.domain.exception;

/**
 * Thrown when an account attempts to sell more shares of an instrument
 * than it currently holds
 */
public class InsufficientHoldingsException extends RuntimeException {

    public InsufficientHoldingsException(String message) {
        super(message);
    }

    public InsufficientHoldingsException(String message, Throwable cause) {
        super(message, cause);
    }

}
