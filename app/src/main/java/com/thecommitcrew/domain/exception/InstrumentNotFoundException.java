package com.thecommitcrew.domain.exception;

/**
 * Thrown when an instrument lookup fails because the requested instrument
 * does not exist or cannot be found in the system.
 */
public class InstrumentNotFoundException extends RuntimeException {

    public InstrumentNotFoundException(String message) {
        super(message);
    }

    public InstrumentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    
}