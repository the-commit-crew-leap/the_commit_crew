package com.thecommitcrew;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Message written to the {@code executions} topic: a full fill of one order. */
public record ExecutionEvent(
        UUID executionId,
        UUID orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        int quantity,
        BigDecimal price,
        BigDecimal limitPrice,
        String venue,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Instant executedOn
) {
}
