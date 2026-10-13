package com.thecommitcrew;

/** Waits for the simulated market. Its own type so tests can skip the wait. */
@FunctionalInterface
public interface Pauser {

    void pause(long duration) throws InterruptedException;
}
