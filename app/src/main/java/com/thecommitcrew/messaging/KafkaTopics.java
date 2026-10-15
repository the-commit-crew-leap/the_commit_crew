package com.thecommitcrew.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the two topics. Spring's KafkaAdmin creates them on startup if
 * they don't exist yet (the broker's own auto-create is switched off), so
 * whichever service starts first sets them up.
 */
@Configuration
public class KafkaTopics {
    
    @Bean
    public NewTopic ordersTopic(@Value("${trading.kafka.topics.orders}") String name,
                                @Value("${trading.kafka.partitions}") int partitions,
                                @Value("${trading.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic executionsTopic(@Value("${trading.kafka.topics.executions}") String name,
                                    @Value("${trading.kafka.partitions}") int partitions,
                                    @Value("${trading.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
    
    @Bean
    public NewTopic deadLettersTopic(@Value("${trading.kafka.topics.dead-letters:dead-letters}") String name,
                                    @Value("${trading.kafka.partitions}") int partitions,
                                    @Value("${trading.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic deadLettersRetryTopic(@Value("${trading.kafka.topics.dead-letters-retry:dead-letters-retry}") String name,
                                        @Value("${trading.kafka.partitions}") int partitions,
                                        @Value("${trading.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}
