package com.thecommitcrew.domain.exception;

/**
 * Thrown when an order is placed with an idempotency key that has already been used.
 * Prevents duplicate orders.
 */
public class DuplicateOrderException extends RuntimeException {

    public DuplicateOrderException(String message) {
        super(message);
    }

    public DuplicateOrderException(String message, Throwable cause) {
        super(message, cause);
    }

}
