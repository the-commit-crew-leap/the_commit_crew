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
    public NewTopic ordersTopic(@Value("${engine.topics.orders}") String name,
                                @Value("${engine.topics.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @SuppressWarnings("null")
    @Bean
    public NewTopic executionsTopic(@Value("${engine.topics.executions}") String name,
                                    @Value("${engine.topics.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
