package com.thecommitcrew.persistence.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class WatchlistIdTest {

    @Test
    void createsDefaultAndParameterizedIds() {
        WatchlistId emptyId = new WatchlistId();
        WatchlistId id = new WatchlistId("ACC-1001", "AAPL");

        assertNotNull(emptyId);
        assertNotNull(id);
    }

    @Test
    void equalIdsHaveEqualHashCodes() {
        WatchlistId first = new WatchlistId("ACC-1001", "AAPL");
        WatchlistId second = new WatchlistId("ACC-1001", "AAPL");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void idsDifferWhenAccountOrSymbolDiffers() {
        WatchlistId original = new WatchlistId("ACC-1001", "AAPL");

        assertNotEquals(original, new WatchlistId("ACC-1002", "AAPL"));
        assertNotEquals(original, new WatchlistId("ACC-1001", "MSFT"));
        assertNotEquals(null, original);
    }

    @Test
    void equalityIsNullSafe() {
        assertEquals(new WatchlistId(null, null), new WatchlistId(null, null));
        assertNotEquals(new WatchlistId(null, "AAPL"), new WatchlistId("ACC-1001", null));
    }
}