package com.thecommitcrew;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Message read from the {@code orders} topic. Mirrors the trade API's
 * {@code OrderEvent}; the two services share the JSON contract documented in
 * {@code docs/event-flow.md}, not a code dependency.
 */
public record OrderEvent(
        UUID orderId,
        long accountId,
        String symbol,
        OrderSide side,
        int quantity,
        BigDecimal price,
        Instant createdOn
) {
}
