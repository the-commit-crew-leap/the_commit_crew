package com.thecommitcrew.domain.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderSideTest {
    @Test
    void buyAppliesAddition() {
        long result = OrderSide.BUY.apply(10L, 5L);
        assertEquals(15L, result);
    }

    @Test
    void sellAppliesSubtraction() {
        long result = OrderSide.SELL.apply(10L, 3L);
        assertEquals(7L, result);
    }

    @Test
    void applyWithZeroQuantity() {
        assertEquals(5L, OrderSide.BUY.apply(0L, 5L));
        assertEquals(0L, OrderSide.SELL.apply(5L, 5L));
    }
}
