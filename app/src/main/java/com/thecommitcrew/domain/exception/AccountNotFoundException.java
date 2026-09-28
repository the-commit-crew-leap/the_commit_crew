package com.thecommitcrew.domain.exception;

/**
 * Thrown when an account lookup fails because the requested account
 * does not exist or cannot be found in the system.
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String message) {
        super(message);
    }

    public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

}
