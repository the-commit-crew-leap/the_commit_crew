package com.thecommitcrew;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Consumes and processes dead-lettered messages from both dead-letter topics.
 * 
 * Responsibilities:
 * - Logs dead-lettered messages with full context for debugging
 * - Separates permanent failures from transient failures (budget exhausted)
 * - Provides visibility into failure patterns and counts
 * - Can be extended to send alerts, store in database, or trigger manual review
 */
@Component
public class DeadLetterHandler {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterHandler.class);
    private static final String ORIGINAL_MESSAGE = "originalMessage";
    private static final String EXCEPTION_TYPE = "exceptionType";
    private static final String EXCEPTION_MESSAGE = "exceptionMessage";
    private static final String TIMESTAMP = "timestamp";

    private final ObjectMapper objectMapper;
    private final EngineProperties engineProperties;

    public DeadLetterHandler(ObjectMapper objectMapper, EngineProperties engineProperties) {
        this.objectMapper = objectMapper;
        this.engineProperties = engineProperties;
    }

    /**
     * Consumes messages from the permanent dead-letter topic.
     * These are messages that failed with non-retryable errors (e.g., invalid JSON).
     */
    @KafkaListener(topics = "${engine.topics.dead-letters}", 
                   groupId = "dead-letter-handler",
                   concurrency = "1")
    public void onPermanentDeadLetter(String message) {
        processPermanentFailure(message);
    }

    /**
     * Consumes messages from the retry budget exhausted dead-letter topic.
     * These are messages that failed transiently but exceeded the retry limit.
     */
    @KafkaListener(topics = "${engine.topics.dead-letters-retry}", 
                   groupId = "dead-letter-handler-retry",
                   concurrency = "1")
    public void onRetryExhaustedDeadLetter(String message) {
        processRetryExhausted(message);
    }

    /**
     * Processes permanent failures (invalid JSON, validation errors, etc).
     * These will never succeed and require manual intervention or data cleanup.
     */
    private void processPermanentFailure(String message) {
        try {
            JsonNode deadLetter = objectMapper.readTree(message);
            
            String originalMessage = deadLetter.get(ORIGINAL_MESSAGE).asText();
            String exceptionType = deadLetter.get(EXCEPTION_TYPE).asText();
            String exceptionMessage = deadLetter.get(EXCEPTION_MESSAGE).asText();
            String timestamp = deadLetter.get(TIMESTAMP).asText();
            
            log.error(
                """
                    PERMANENT DEAD LETTER - Message will never succeed
                    Timestamp: {}
                    Exception Type: {}
                    Exception Message: {}
                    Original Message: {}
                    ACTION REQUIRED: Review and fix data quality or application logic
                """,
                timestamp, exceptionType, exceptionMessage, originalMessage
            );
            
            // TODO: Send to monitoring/alerting system
            // TODO: Store in dead-letter database for analysis
            // TODO: Trigger manual review workflow
            
        } catch (Exception e) {
            log.error("Failed to process permanent dead letter message: {}", message, e);
        }
    }

    /**
     * Processes transient failures that exceeded the retry budget.
     * These may succeed if retried later with fresh resources, but the budget has been spent.
     */
    private void processRetryExhausted(String message) {
        try {
            JsonNode deadLetter = objectMapper.readTree(message);
            
            String originalMessage = deadLetter.get(ORIGINAL_MESSAGE).asText();
            int attemptNumber = deadLetter.get("attemptNumber").asInt();
            String exceptionType = deadLetter.get(EXCEPTION_TYPE).asText();
            String exceptionMessage = deadLetter.get(EXCEPTION_MESSAGE).asText();
            String timestamp = deadLetter.get(TIMESTAMP).asText();
            
            log.error(
                """
                    RETRY BUDGET EXHAUSTED - Transient failure exceeded max retries
                    Timestamp: {}
                    Attempts Made: {} (max: {})
                    Exception Type: {}
                    Exception Message: {}
                    Original Message: {}
                    ACTION POSSIBLE: Could retry with increased budget or after system recovery
                """,
                timestamp, attemptNumber, engineProperties.failure().maxRetries(),
                exceptionType, exceptionMessage, originalMessage
            );
            
            // TODO: Send to monitoring/alerting system
            // TODO: Store for potential replay once system is healthy
            // TODO: Trigger incident response if threshold exceeded
            
        } catch (Exception e) {
            log.error("Failed to process retry exhausted dead letter message: {}", message, e);
        }
    }

    /**
     * Helper to extract dead-letter metadata for external systems.
     * Can be called by monitoring/alerting integrations.
     */
    public DeadLetterMetadata extractMetadata(String deadLetterMessage) throws JsonProcessingException, DateTimeParseException {
        JsonNode node = objectMapper.readTree(deadLetterMessage);

        return new DeadLetterMetadata(
            node.get(ORIGINAL_MESSAGE).asText(),
            node.get("attemptNumber").asInt(),
            node.get("isPermanentFailure").asBoolean(),
            node.get(EXCEPTION_TYPE).asText(),
            node.get(EXCEPTION_MESSAGE).asText(),
            Instant.parse(node.get(TIMESTAMP).asText())
        );
    }

    /**
     * Metadata extracted from a dead-letter message for external processing.
     */
    public record DeadLetterMetadata(
            String originalMessage,
            int attemptNumber,
            boolean isPermanentFailure,
            String exceptionType,
            String exceptionMessage,
            Instant timestamp
    ) {}
}