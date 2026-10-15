package com.thecommitcrew;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DeadLetterHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EngineProperties properties = new EngineProperties(
            java.time.Duration.ZERO,
            java.time.Duration.ZERO,
            0,
            "SIM",
            new EngineProperties.Topics(
                    "orders",
                    "executions",
                    "dead-letters",
                    "dead-letters-retry",
                    1,
                    1
            ),
            new EngineProperties.Failure(3, 100, 2.0, 300)
    );

    private final DeadLetterHandler handler =
            new DeadLetterHandler(objectMapper, properties);

    @Test
    void extractMetadata_readsAllFields() throws Exception {
        Instant timestamp = Instant.parse("2026-01-01T12:00:00Z");

        String payload = """
                {
                  "originalMessage": "{\\"orderId\\":\\"123\\"}",
                  "attemptNumber": 2,
                  "isPermanentFailure": false,
                  "exceptionType": "TimeoutException",
                  "exceptionMessage": "Broker timed out",
                  "timestamp": "2026-01-01T12:00:00Z"
                }
                """;

        DeadLetterHandler.DeadLetterMetadata metadata =
                handler.extractMetadata(payload);

        assertEquals("{\"orderId\":\"123\"}", metadata.originalMessage());
        assertEquals(2, metadata.attemptNumber());
        assertFalse(metadata.isPermanentFailure());
        assertEquals("TimeoutException", metadata.exceptionType());
        assertEquals("Broker timed out", metadata.exceptionMessage());
        assertEquals(timestamp, metadata.timestamp());
    }

    @Test
    void extractMetadata_readsPermanentFailure() throws Exception {
        String payload = """
                {
                  "originalMessage": "invalid-json",
                  "attemptNumber": 1,
                  "isPermanentFailure": true,
                  "exceptionType": "JsonProcessingException",
                  "exceptionMessage": "Invalid JSON",
                  "timestamp": "2026-01-01T12:00:00Z"
                }
                """;

        DeadLetterHandler.DeadLetterMetadata metadata =
                handler.extractMetadata(payload);

        assertTrue(metadata.isPermanentFailure());
        assertEquals(1, metadata.attemptNumber());
        assertEquals("invalid-json", metadata.originalMessage());
    }

    @Test
    void extractMetadata_rejectsMalformedJson() {
        assertThrows(
                com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> handler.extractMetadata("not-json")
        );
    }

    @Test
    void extractMetadata_rejectsInvalidTimestamp() {
        String payload = """
                {
                  "originalMessage": "message",
                  "attemptNumber": 1,
                  "isPermanentFailure": true,
                  "exceptionType": "Exception",
                  "exceptionMessage": "Failure",
                  "timestamp": "invalid-timestamp"
                }
                """;

        assertThrows(
                java.time.format.DateTimeParseException.class,
                () -> handler.extractMetadata(payload)
        );
    }

    @Test
    void onPermanentDeadLetter_handlesValidPayload() {
        String payload = validPayload(true);

        assertDoesNotThrow(() -> handler.onPermanentDeadLetter(payload));
    }

    @Test
    void onRetryExhaustedDeadLetter_handlesValidPayload() {
        String payload = validPayload(false);

        assertDoesNotThrow(() -> handler.onRetryExhaustedDeadLetter(payload));
    }

    @Test
    void listenerMethods_swallowMalformedPayloads() {
        assertDoesNotThrow(() -> handler.onPermanentDeadLetter("not-json"));
        assertDoesNotThrow(() -> handler.onRetryExhaustedDeadLetter("not-json"));
    }

    private String validPayload(boolean permanent) {
        return """
                {
                  "originalMessage": "original-message",
                  "attemptNumber": 3,
                  "isPermanentFailure": %s,
                  "exceptionType": "TimeoutException",
                  "exceptionMessage": "Temporary failure",
                  "timestamp": "2026-01-01T12:00:00Z"
                }
                """.formatted(permanent);
    }
}