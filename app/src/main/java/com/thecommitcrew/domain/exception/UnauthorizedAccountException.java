package com.thecommitcrew.domain.exception;

public class UnauthorizedAccountException extends RuntimeException {
    public UnauthorizedAccountException(String message) {
        super(message);
    }
}