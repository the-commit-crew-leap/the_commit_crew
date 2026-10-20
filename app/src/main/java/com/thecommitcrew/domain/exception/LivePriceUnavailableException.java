package com.thecommitcrew.domain.exception;

public class LivePriceUnavailableException extends RuntimeException {
    public LivePriceUnavailableException(String message) {
        super(message);
    }

    public LivePriceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
