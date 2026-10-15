package com.thecommitcrew;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Classifies and handles execution failures, distinguishing between permanent failures
 * (dead-lettered immediately) and transient failures (retried with exponential backoff).
 */
public class FailureStrategy {

    private final EngineProperties.Failure properties;

    public FailureStrategy(EngineProperties.Failure properties) {
        this.properties = properties;
    }

    /**
     * Determines if a failure is retryable (transient).
     */
    public boolean isRetryable(Exception exception, int attemptNumber) {
        if (attemptNumber > properties.maxRetries()) {
            return false;
        }
        return isTransientFailure(exception);
    }

    /**
     * Determines if an exception represents a transient (retriable) failure.
     */
    private boolean isTransientFailure(Exception exception) {
        Throwable cause = exception;

        while (cause instanceof ExecutionException
                || cause instanceof org.springframework.kafka.listener.ListenerExecutionFailedException) {
            cause = cause.getCause();
        }

        return cause instanceof TimeoutException
                || cause instanceof org.springframework.kafka.KafkaException
                || cause instanceof IOException;
    }

    /**
     * Calculates backoff duration with exponential growth.
     */
    public Duration calculateBackoff(int attemptNumber) {
        long delayMs = properties.initialBackoffMs();
        double multiplier = properties.backoffMultiplier();
        
        for (int i = 1; i < attemptNumber; i++) {
            delayMs = (long) (delayMs * multiplier);
        }
        
        long maxBackoffMs = properties.maxBackoffMs();
        return Duration.ofMillis(Math.min(delayMs, maxBackoffMs));
    }

    /**
     * Determines if a message is unrecoverable and should be dead-lettered.
     */
    public boolean isPermanentFailure(Exception exception) {
        return !isTransientFailure(exception);
    }
}