package com.thecommitcrew;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class FailureStrategyTest {

    private final EngineProperties.Failure properties =
            new EngineProperties.Failure(3, 100, 2.0, 300);

    private final FailureStrategy strategy = new FailureStrategy(properties);

    @Test
    void retryableFailuresAreAcceptedWithinRetryLimit() {
        assertTrue(strategy.isRetryable(new TimeoutException(), 1));
        assertTrue(strategy.isRetryable(new KafkaException("temporary failure"), 2));
        assertTrue(strategy.isRetryable(new IOException(), 3));
    }

    @Test
    void retryableFailureIsRejectedAfterRetryLimit() {
        assertFalse(strategy.isRetryable(new TimeoutException(), 4));
    }

    @Test
    void permanentFailuresAreNotRetryable() {
        assertFalse(strategy.isRetryable(new IllegalArgumentException(), 1));
        assertTrue(strategy.isPermanentFailure(new IllegalArgumentException()));
    }

    @Test
    void transientFailuresAreNotPermanent() {
        assertFalse(strategy.isPermanentFailure(new TimeoutException()));
        assertFalse(strategy.isPermanentFailure(new KafkaException("temporary failure")));
        assertFalse(strategy.isPermanentFailure(new IOException()));
    }

    @Test
    void calculatesExponentialBackoff() {
        assertEquals(Duration.ofMillis(100), strategy.calculateBackoff(1));
        assertEquals(Duration.ofMillis(200), strategy.calculateBackoff(2));
        assertEquals(Duration.ofMillis(300), strategy.calculateBackoff(3));
    }

    @Test
    void capsBackoffAtMaximum() {
        assertEquals(Duration.ofMillis(300), strategy.calculateBackoff(5));
    }

    @Test
    void unknownExceptionIsPermanent() {
        assertTrue(strategy.isPermanentFailure(new RuntimeException("unknown failure")));
        assertFalse(strategy.isRetryable(new RuntimeException("unknown failure"), 1));
    }
}