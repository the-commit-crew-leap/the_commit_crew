package com.thecommitcrew.domain.exception;

/**
 * Thrown when the requested account is not marked as ACTIVE in the system.
 */
public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(String message) {
        super(message);
    }

    public AccountNotActiveException(String message, Throwable cause) {
        super(message, cause);
    }

}
