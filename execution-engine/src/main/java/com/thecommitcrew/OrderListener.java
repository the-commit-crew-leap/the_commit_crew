package com.thecommitcrew;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Step 2 of the event flow: reads each order from the {@code orders} topic,
 * works it on the {@link SimulatedMarket}, and writes the fill to the
 * {@code executions} topic keyed by account.
 *
 * <p>Implements failure handling with classification:
 * - Permanent failures (e.g., invalid JSON) are dead-lettered immediately
 * - Transient failures (e.g., timeouts) are retried with exponential backoff
 * - No message is retried indefinitely; retries stop when budget is exhausted
 * - At-least-once delivery: order's offset committed only after execution is written
 */
@Component
public class OrderListener {

    private static final Logger log = LoggerFactory.getLogger(OrderListener.class);
    private static final int INITIAL_ATTEMPT = 1;

    private final SimulatedMarket market;
    private final Pauser pauser;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final EngineProperties engineProperties;
    private final FailureStrategy failureStrategy;

    public OrderListener(SimulatedMarket market, Pauser pauser, KafkaTemplate<String, String> kafkaTemplate,
                         ObjectMapper objectMapper, EngineProperties engineProperties, 
                         FailureStrategy failureStrategy) {
        this.market = market;
        this.pauser = pauser;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.engineProperties = engineProperties;
        this.failureStrategy = failureStrategy;
    }

    @KafkaListener(topics = "${engine.topics.orders}", groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${engine.topics.partitions}")
    public void onOrder(String message) throws InterruptedException {
        processOrderWithRetry(message, INITIAL_ATTEMPT);
    }

    /**
     * Processes an order with retry logic. Retries on transient failures with exponential backoff.
     * Dead-letters on permanent failures or when retry budget is exhausted.
     */
    private void processOrderWithRetry(String message, int attemptNumber) throws InterruptedException {
        try {
            OrderEvent order = deserializeOrder(message);
            executeOrder(order);
            log.info("Successfully processed order {}", order.orderId());
        } catch (JsonProcessingException e) {
            // Permanent failure: invalid JSON/deserialization error
            log.error("Permanent failure: invalid JSON for message: {}", message, e);
            deadLetterMessage(message, e, attemptNumber, true);
        } catch (TimeoutException | ExecutionException e) {
            // Transient failure: execution or Kafka timeout
            handleTransientFailure(message, e, attemptNumber);
        } catch (InterruptedException e) {
            // Preserve the thread's interruption status
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            // Unknown exception: retry if possible, otherwise dead-letter
            if (failureStrategy.isRetryable(e, attemptNumber)) {
                handleTransientFailure(message, e, attemptNumber);
            } else {
                log.error("Permanent failure: unrecoverable error for message: {}", message, e);
                deadLetterMessage(message, e, attemptNumber, false);
            }
        }
    }

    /**
     * Handles transient failures by retrying with exponential backoff.
     */
    private void handleTransientFailure(String message, Exception exception, int attemptNumber) 
            throws InterruptedException {
        
        if (!failureStrategy.isRetryable(exception, attemptNumber)) {
            // Retry budget exhausted
            log.error("Retry budget exhausted for message after {} attempts: {}", attemptNumber, message, exception);
            deadLetterMessage(message, exception, attemptNumber, false);
            return;
        }

        // Calculate backoff and retry
        Duration backoff = failureStrategy.calculateBackoff(attemptNumber);
        log.warn("Transient failure on attempt {}, retrying after {} ms: {}", 
                attemptNumber, backoff.toMillis(), exception.getMessage());
        
        pauser.pause(backoff.toMillis());
        processOrderWithRetry(message, attemptNumber + 1);
    }

    /**
     * Deserializes an order message, throwing JsonProcessingException on invalid JSON.
     */
    private OrderEvent deserializeOrder(String message) throws JsonProcessingException {
        log.info("-----> Reading Message {}", message);
        return objectMapper.readValue(message, OrderEvent.class);
    }

    /**
     * Executes an order and sends the fill to the executions topic.
     * @throws JsonProcessingException 
     */
    private void executeOrder(OrderEvent order) throws ExecutionException, TimeoutException, InterruptedException, JsonProcessingException {
        log.info("Working order {}: {} {} {} limit {}", order.orderId(), order.side(), order.quantity(),
                order.symbol(), order.price());
        
        pauser.pause(market.nextDelay().toMillis()); 
        ExecutionEvent fill = market.execute(order);

        // Send execution to Kafka with timeout
        kafkaTemplate.send(engineProperties.topics().executions(), 
                          fill.accountId(), 
                          objectMapper.writeValueAsString(fill))
                .get(10, TimeUnit.SECONDS);
        
        log.info("Filled order {} at {} on {}", fill.orderId(), fill.price(), fill.venue());
    }

    /**
     * Sends a message to the dead-letter topic for unrecoverable failures.
     */
    private void deadLetterMessage(String message, Exception exception, int attemptNumber, boolean isPermanent) {
        String topic = isPermanent ? engineProperties.topics().deadLetters() 
                                   : engineProperties.topics().deadLettersRetry();
        
        try {
            String deadLetterPayload = createDeadLetterPayload(message, exception, attemptNumber, isPermanent);
            kafkaTemplate.send(topic, deadLetterPayload)
                    .get(10, TimeUnit.SECONDS);
            String failureType = isPermanent ? "PERMANENT" : "TRANSIENT (budget exhausted)";
            log.error("Dead-lettered {} failure to topic '{}' after {} attempts: {}",
                    failureType, topic, attemptNumber, exception.getMessage()
            );
        } catch (InterruptedException e) {
            // Preserve the thread's interruption status
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while dead-lettering message", e);
        } catch (Exception e) {
            log.error("Failed to dead-letter message to {}: {}", topic, message, e);
        }
    }

    /**
     * Creates a dead-letter envelope with metadata for debugging.
     */
    private String createDeadLetterPayload(String message, Exception exception, 
                                          int attemptNumber, boolean isPermanent) 
            throws com.fasterxml.jackson.core.JsonProcessingException {
        
        var deadLetter = new java.util.LinkedHashMap<String, Object>();
        deadLetter.put("originalMessage", message);
        deadLetter.put("attemptNumber", attemptNumber);
        deadLetter.put("isPermanentFailure", isPermanent);
        deadLetter.put("exceptionType", exception.getClass().getSimpleName());
        deadLetter.put("exceptionMessage", exception.getMessage());
        deadLetter.put("timestamp", java.time.Instant.now());
        
        return objectMapper.writeValueAsString(deadLetter);
    }
}