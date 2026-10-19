package com.thecommitcrew;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderListenerTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private Duration paused;

    private OrderListener listener() {
        return listener(3, d -> paused = Duration.ofMillis(d));
    }

    private OrderListener listener(int maxRetries, Pauser pauser) {
        EngineProperties.Topics topics = new EngineProperties.Topics(
                "orders", "executions", "dead-letters", "dead-letters-retry", 3, 1
        );

        EngineProperties.Failure failure =
                new EngineProperties.Failure(maxRetries, 100, 2.0, 30_000);

        EngineProperties props = new EngineProperties(
                Duration.ofMillis(750),
                Duration.ofMillis(750),
                0,
                "SIM",
                topics,
                failure
        );

        SimulatedMarket market = new SimulatedMarket(
                props,
                new Random(1),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        );

        return new OrderListener(
                market,
                pauser,
                kafka,
                mapper,
                props,
                new FailureStrategy(failure)
        );
    }

    @Test
    void testOnOrder_WaitsForMarketThenPublishesFillKeyedByAccount() throws Exception {
        @SuppressWarnings("unchecked")
        SendResult<String, String> sent = mock(SendResult.class);
        when(kafka.send(eq("executions"), eq("ACC-1001"), anyString())).thenReturn(CompletableFuture.completedFuture(sent));
        
        OrderEvent order = new OrderEvent(
            UUID.randomUUID(), 
            "ACC-1001",  
            "VERDA", 
            OrderSide.SELL, 
            5,
            new BigDecimal("4.20"),
            Instant.EPOCH
        );

        listener().onOrder(mapper.writeValueAsString(order));

        assertEquals(Duration.ofMillis(750), paused);
        verify(kafka).send(eq("executions"), eq("ACC-1001"), anyString());
    }

    @Test
    void testOnOrder_UnreadableMessage_SkippedWithoutPublishing() throws Exception {
        // Mock the dead-letter send
        when(kafka.send(eq("dead-letters"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        
        listener().onOrder("not json");

        // Should dead-letter the invalid JSON, not skip silently
        verify(kafka).send(eq("dead-letters"), anyString());
    }

    @Test
    void testOnOrder_TransientFailure_RetriesAndEventuallySucceeds() throws Exception {
        @SuppressWarnings("unchecked")
        SendResult<String, String> sent = mock(SendResult.class);

        List<Duration> pauses = new ArrayList<>();

        when(kafka.send(eq("executions"), eq("ACC-1001"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker timed out")),
                        CompletableFuture.completedFuture(sent));

        OrderEvent order = new OrderEvent(
                UUID.randomUUID(),
                "ACC-1001",
                "VERDA",
                OrderSide.SELL,
                5,
                new BigDecimal("4.20"),
                Instant.EPOCH
        );

        listener(3, duration -> pauses.add(Duration.ofMillis(duration)))
                .onOrder(mapper.writeValueAsString(order));

        verify(kafka, times(2))
                .send(eq("executions"), eq("ACC-1001"), anyString());

        assertEquals(
                List.of(
                        Duration.ofMillis(750),
                        Duration.ofMillis(100),
                        Duration.ofMillis(750)
                ),
                pauses
        );
    }

    @Test
    void testOnOrder_TransientFailure_RetryBudgetExhausted_DeadLettersMessage() throws Exception {
        when(kafka.send(eq("executions"), eq("ACC-1001"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker timed out")));

        when(kafka.send(eq("dead-letters-retry"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        OrderEvent order = new OrderEvent(
                UUID.randomUUID(),
                "ACC-1001",
                "VERDA",
                OrderSide.SELL,
                5,
                new BigDecimal("4.20"),
                Instant.EPOCH
        );

        listener().onOrder(mapper.writeValueAsString(order));

        verify(kafka, times(4))
                .send(eq("executions"), eq("ACC-1001"), anyString());
        verify(kafka).send(eq("dead-letters-retry"), anyString());
    }

    @Test
    void testOnOrder_UnknownFailure_DeadLettersWithoutRetry() throws Exception {
        when(kafka.send(eq("dead-letters-retry"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        Pauser failingPauser = duration -> {
            throw new IllegalStateException("market unavailable");
        };

        OrderEvent order = new OrderEvent(
                UUID.randomUUID(),
                "ACC-1001",
                "VERDA",
                OrderSide.SELL,
                5,
                new BigDecimal("4.20"),
                Instant.EPOCH
        );

        listener(3, failingPauser).onOrder(mapper.writeValueAsString(order));

        verify(kafka).send(eq("dead-letters-retry"), anyString());
        verify(kafka, times(0))
                .send(eq("executions"), eq("ACC-1001"), anyString());
    }

    @Test
    void testOnOrder_InterruptedDuringExecution_RethrowsAndPreservesInterrupt() {
        Pauser interruptedPauser = duration -> {
            throw new InterruptedException("interrupted");
        };

        OrderEvent order = new OrderEvent(
                UUID.randomUUID(),
                "ACC-1001",
                "VERDA",
                OrderSide.SELL,
                5,
                new BigDecimal("4.20"),
                Instant.EPOCH
        );

        try {
            assertThrows(
                    InterruptedException.class,
                    () -> listener(3, interruptedPauser)
                            .onOrder(mapper.writeValueAsString(order))
            );

            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }

        verify(kafka, times(0)).send(anyString(), anyString());
    }
}
