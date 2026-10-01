package com.thecommitcrew;

import com.thecommitcrew.Side;
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
        Long accountId,
        String symbol,
        Side side,
        int quantity,
        BigDecimal limitPrice,
        BigDecimal price,
        Instant createdOn
) {
}
