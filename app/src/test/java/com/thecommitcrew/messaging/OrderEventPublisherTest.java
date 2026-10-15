package com.thecommitcrew.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thecommitcrew.domain.enums.OrderSide;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    private static final String TOPIC = "orders";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        publisher = new OrderEventPublisher(kafkaTemplate, objectMapper, TOPIC);
    }

    @Test
    void publishSerializesEventAndSendsItKeyedByAccount() throws Exception {
        OrderEvent event = event();
        CompletableFuture<SendResult<String, String>> sendResult = new CompletableFuture<>();
        when(kafkaTemplate.send(eq(TOPIC), eq(event.accountId()), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(sendResult);

        publisher.publish(event);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(event.accountId()), jsonCaptor.capture());
        JsonNode expectedJson = objectMapper.valueToTree(event);
        JsonNode actualJson = objectMapper.readTree(jsonCaptor.getValue());

        assertEquals(expectedJson.get("orderId"), actualJson.get("orderId"));
        assertEquals(expectedJson.get("accountId"), actualJson.get("accountId"));
        assertEquals(expectedJson.get("symbol"), actualJson.get("symbol"));
        assertEquals(expectedJson.get("side"), actualJson.get("side"));
        assertEquals(expectedJson.get("quantity"), actualJson.get("quantity"));
        assertEquals(0, expectedJson.get("price").decimalValue()
                .compareTo(actualJson.get("price").decimalValue()));
        assertEquals(expectedJson.get("createdOn"), actualJson.get("createdOn"));
    }

    @Test
    void publishLogsFailedSendWithoutThrowing() {
        OrderEvent event = event();
        CompletableFuture<SendResult<String, String>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(eq(TOPIC), eq(event.accountId()), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(failedSend);

        assertDoesNotThrow(() -> publisher.publish(event));
    }

    @Test
    void publishThrowsAndDoesNotSendWhenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        OrderEvent event = event();
        when(failingMapper.writeValueAsString(event))
                .thenThrow(new JsonProcessingException("serialization failed") {
                });
        OrderEventPublisher failingPublisher = new OrderEventPublisher(kafkaTemplate, failingMapper, TOPIC);

        assertThrows(IllegalStateException.class, () -> failingPublisher.publish(event));

        verifyNoInteractions(kafkaTemplate);
    }

    private OrderEvent event() {
        return new OrderEvent(
                UUID.randomUUID(),
                "ACC-1001",
                "AAPL",
                OrderSide.BUY,
                10,
                new BigDecimal("100.00"),
                Instant.parse("2026-01-01T12:00:00Z"));
    }
}
