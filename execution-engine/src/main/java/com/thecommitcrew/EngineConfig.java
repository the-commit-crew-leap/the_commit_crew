package com.thecommitcrew;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Clock;
import java.util.Random;

@Configuration
public class EngineConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new com.fasterxml.jackson.module.paramnames.ParameterNamesModule());
        mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    @Bean
    public SimulatedMarket simulatedMarket(EngineProperties properties) {
        // java.util.Random is thread-safe (the listener runs one thread per partition) and lives in
        // java.base, unlike RandomGenerator.getDefault(), whose jdk.random module the slim JRE image lacks.
        return new SimulatedMarket(properties, new Random(), Clock.systemUTC());
    }

    @Bean
    public Pauser pauser() {
        return Thread::sleep;
    }

    @SuppressWarnings("null")
    @Bean
    public NewTopic ordersTopic(EngineProperties properties) {
        return TopicBuilder.name(properties.topics().orders())
                .partitions(properties.topics().partitions())
                .replicas(1)
                .build();
    }

    @SuppressWarnings("null")
    @Bean
    public NewTopic executionsTopic(EngineProperties properties) {
        return TopicBuilder.name(properties.topics().executions())
                .partitions(properties.topics().partitions())
                .replicas(1)
                .build();
    }
    
    @SuppressWarnings("null")
    @Bean 
    public FailureStrategy failureStrategy(EngineProperties properties) {
        return new FailureStrategy(properties.failure());
    }

    @SuppressWarnings("null")
    @Bean
    public DeadLetterHandler deadLetterHandler(ObjectMapper objectMapper, EngineProperties properties) {
        return new DeadLetterHandler(objectMapper, properties);
    }
}