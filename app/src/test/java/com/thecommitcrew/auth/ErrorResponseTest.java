package com.thecommitcrew.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ErrorResponseTest {

    @Test
    void constructor_storesAllValues() {
        Object data = new Object();

        ErrorResponse response =
                new ErrorResponse("ERROR", "Unauthorized", data);

        assertEquals("ERROR", response.getStatus());
        assertEquals("Unauthorized", response.getMessage());
        assertEquals(data, response.getData());
    }

    @Test
    void constructor_acceptsNullData() {
        ErrorResponse response =
                new ErrorResponse("ERROR", "Request failed", null);

        assertEquals("ERROR", response.getStatus());
        assertEquals("Request failed", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void constructor_acceptsNullValues() {
        ErrorResponse response =
                new ErrorResponse(null, null, null);

        assertNull(response.getStatus());
        assertNull(response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void data_canContainStructuredObjects() {
        Object data = java.util.Map.of(
                "field", "token",
                "reason", "expired"
        );

        ErrorResponse response =
                new ErrorResponse("ERROR", "Validation failed", data);

        assertEquals(data, response.getData());
    }
}