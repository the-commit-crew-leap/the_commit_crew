package com.thecommitcrew;

import java.time.Duration;

/** Waits for the simulated market. Its own type so tests can skip the wait. */
@FunctionalInterface
public interface Pauser {

    void pause(Duration duration) throws InterruptedException;
}
