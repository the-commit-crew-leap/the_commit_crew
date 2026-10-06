package com.thecommitcrew;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EnginePropertiesTest {

    @Test
    void testMaxDelayBelowMinDelay_Rejected() {
        Duration maxDelay = Duration.ofSeconds(2);
        Duration minDelay = Duration.ofSeconds(1);
        assertThrows(IllegalArgumentException.class,
                () -> new EngineProperties(maxDelay, minDelay, 50, "SIM"));
    }

    @Test
    void testPriceImprovementOutOfRange_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EngineProperties(Duration.ZERO, Duration.ZERO, 10_000, "SIM"));
    }
}
