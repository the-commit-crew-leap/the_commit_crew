package com.thecommitcrew;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tuning for the simulated market, bound from {@code engine.*}.
 *
 * @param minDelay               shortest time the market takes to fill an order
 * @param maxDelay               longest time the market takes to fill an order
 * @param maxPriceImprovementBps largest price improvement over the limit, in basis points (1 bp = 0.01%)
 * @param venue                  name stamped on every fill
 * @param topics                 Kafka topic configuration
 * @param failure                Failure handling and retry strategy configuration
 */
@ConfigurationProperties("engine")
public record EngineProperties(
        Duration minDelay,
        Duration maxDelay,
        int maxPriceImprovementBps,
        String venue,
        Topics topics,
        Failure failure
) {
    public EngineProperties {
        if (minDelay.isNegative() || maxDelay.compareTo(minDelay) < 0) {
            throw new IllegalArgumentException("engine delays must satisfy 0 <= min-delay <= max-delay");
        }
        if (maxPriceImprovementBps < 0 || maxPriceImprovementBps >= 10_000) {
            throw new IllegalArgumentException("engine.max-price-improvement-bps must be between 0 and 9999");
        }
    }

    public record Topics(
            String orders,
            String executions,
            String deadLetters,
            String deadLettersRetry,
            int partitions,
            int replicas
    ) {}

    public record Failure(
            int maxRetries,
            long initialBackoffMs,
            double backoffMultiplier,
            long maxBackoffMs
    ) {}
}
