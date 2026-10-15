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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderListenerTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private Duration paused;

private OrderListener listener() {
    // Create full EngineProperties with all nested records
    EngineProperties.Topics topics = new EngineProperties.Topics(
        "orders", "executions", "dead-letters", "dead-letters-retry", 3, 1
    );
    EngineProperties.Failure failure = new EngineProperties.Failure(3, 100, 2.0, 30000);
    EngineProperties props = new EngineProperties(
        Duration.ofMillis(750), 
        Duration.ofMillis(750), 
        0, 
        "SIM", 
        topics,
        failure
    );
    
    SimulatedMarket market = new SimulatedMarket(props, new Random(1), 
                                                  Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    
    FailureStrategy strategy = new FailureStrategy(failure);
    Pauser pauser = d -> paused = Duration.ofMillis(d); 
    
    return new OrderListener(market, pauser, kafka, mapper, props, strategy);
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
}
