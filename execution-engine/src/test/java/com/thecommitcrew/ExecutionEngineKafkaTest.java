package com.thecommitcrew;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the whole engine against an in-process Kafka broker: an order
 * published to {@code orders} comes back as a fill on {@code executions}.
 * No Docker needed.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "engine.min-delay=0ms",
        "engine.max-delay=0ms",
        "engine.failure.max-retries=1",
        "engine.failure.initial-backoff-ms=1",
        "server.port=0"
})
@EmbeddedKafka(partitions = 1, topics = {"orders", "executions", "dead-letters", "dead-letters-retry"})
class ExecutionEngineKafkaTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void testOrderOnOrdersTopic_ProducesFillOnExecutionsTopic() throws Exception {
        OrderEvent order = new OrderEvent(
                UUID.randomUUID(),
                "ACC-1001",
                "ACME",
                OrderSide.BUY,
                10,
                new BigDecimal("25.50"),
                Instant.now()
        );

        try (Consumer<String, String> consumer = executionsConsumer()) {
            kafkaTemplate.send(
                    "orders",
                    order.accountId(),
                    objectMapper.writeValueAsString(order)
            ).get();

            ConsumerRecord<String, String> matchingRecord = null;
            long deadline = System.currentTimeMillis() + 20_000;

            while (matchingRecord == null && System.currentTimeMillis() < deadline) {
                
                for (ConsumerRecord<String, String> executionRecord : consumer.poll(Duration.ofSeconds(1))) {

                    ExecutionEvent fill = objectMapper.readValue(executionRecord.value(), ExecutionEvent.class);

                    if (fill.orderId().equals(order.orderId())) {
                        matchingRecord = executionRecord;
                        break;
                    }
                }
            }

            assertNotNull(matchingRecord, "Expected an execution for the submitted order");

            ExecutionEvent fill = objectMapper.readValue(matchingRecord.value(), ExecutionEvent.class);

            assertEquals(order.accountId(), matchingRecord.key());
            assertEquals(order.orderId(), fill.orderId());
            assertEquals(order.quantity(), fill.quantity());
            assertTrue(fill.price().compareTo(order.price()) <= 0);
            assertTrue(matchingRecord.value().contains("\"executedOn\":\""), "timestamps should be ISO strings");
        }
    }


    @Test
    void testSellOrder_ProducesFillAtOrAboveLimitPrice() throws Exception {
        OrderEvent order = new OrderEvent(
                UUID.randomUUID(),
                "ACC-SELL",
                "ACME",
                OrderSide.SELL,
                7,
                new BigDecimal("25.50"),
                Instant.now()
        );

        try (Consumer<String, String> consumer = executionsConsumer()) {

            kafkaTemplate.send(
                    "orders",
                    order.accountId(),
                    objectMapper.writeValueAsString(order)
            ).get();

            ConsumerRecord<String, String> executionRecord =
                    KafkaTestUtils.getSingleRecord(
                            consumer,
                            "executions",
                            Duration.ofSeconds(20)
                    );

            ExecutionEvent fill = objectMapper.readValue(executionRecord.value(), ExecutionEvent.class);

            assertEquals(order.accountId(), executionRecord.key());
            assertEquals(order.orderId(), fill.orderId());
            assertEquals(order.quantity(), fill.quantity());
            assertTrue(fill.price().compareTo(order.price()) >= 0, "SELL fills must be at or above the limit price");
        }
    }

    @Test
    void testMultipleOrders_ProducesMatchingFillsAndKeys() throws Exception {
        OrderEvent first = new OrderEvent(
                UUID.randomUUID(),
                "ACC-1",
                "ACME",
                OrderSide.BUY,
                3,
                new BigDecimal("10.00"),
                Instant.now()
        );

        OrderEvent second = new OrderEvent(
                UUID.randomUUID(),
                "ACC-2",
                "ACME",
                OrderSide.SELL,
                8,
                new BigDecimal("20.00"),
                Instant.now()
        );

        try (Consumer<String, String> consumer = executionsConsumer()) {
            kafkaTemplate.send(
                    "orders",
                    first.accountId(),
                    objectMapper.writeValueAsString(first)
            ).get();

            kafkaTemplate.send(
                    "orders",
                    second.accountId(),
                    objectMapper.writeValueAsString(second)
            ).get();

            Map<UUID, ConsumerRecord<String, String>> recordsByOrderId = new HashMap<>();

            long deadline = System.currentTimeMillis() + 20_000;

            while (recordsByOrderId.size() < 2 && System.currentTimeMillis() < deadline) {
                
                for (ConsumerRecord<String, String> executionRecord : consumer.poll(Duration.ofSeconds(1))) {

                    ExecutionEvent fill = objectMapper.readValue(executionRecord.value(), ExecutionEvent.class);

                    if (fill.orderId().equals(first.orderId()) || fill.orderId().equals(second.orderId())) {
                        recordsByOrderId.put(fill.orderId(), executionRecord);
                    }
                }
            }

            assertEquals(2, recordsByOrderId.size());

            ConsumerRecord<String, String> firstRecord = recordsByOrderId.get(first.orderId());
            ConsumerRecord<String, String> secondRecord = recordsByOrderId.get(second.orderId());

            assertEquals(first.accountId(), firstRecord.key());
            assertEquals(second.accountId(), secondRecord.key());
        }
    }

    @Test
    void testInvalidOrder_IsPublishedToPermanentDeadLetterTopic() throws Exception {
        try (Consumer<String, String> consumer = topicConsumer("dead-letters", "dead-letter-test")) {

            kafkaTemplate.send("orders", "ACC-BAD", "not valid json").get();

            ConsumerRecord<String, String> executionRecord =
                    KafkaTestUtils.getSingleRecord(
                            consumer,
                            "dead-letters",
                            Duration.ofSeconds(20)
                    );

            var deadLetter = objectMapper.readTree(executionRecord.value());

            assertEquals("not valid json", deadLetter.get("originalMessage").asText());
            assertEquals(1, deadLetter.get("attemptNumber").asInt());
            assertTrue(deadLetter.get("isPermanentFailure").asBoolean());
            assertEquals("JsonParseException", deadLetter.get("exceptionType").asText());
            assertTrue(deadLetter.hasNonNull("timestamp"));
        }
    }

    private Consumer<String, String> topicConsumer(String topic, String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId + "-" + UUID.randomUUID(), "true", broker);

        props.put("auto.offset.reset", "earliest");

        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<>(
                        props,
                        new StringDeserializer(),
                        new StringDeserializer()
                ).createConsumer();

        broker.consumeFromAnEmbeddedTopic(consumer, topic);
        return consumer;
    }

    private Consumer<String, String> executionsConsumer() {
        return topicConsumer("executions", "execution-test");
    }
}
