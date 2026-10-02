package com.thecommitcrew.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {
    
    private static final BigDecimal NEGATIVE_AMOUNT = new BigDecimal("-1000.00");

    @Test
    void testDebit_NegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Money(NEGATIVE_AMOUNT));
    }

    @Test
    void testCredit_NegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Money(NEGATIVE_AMOUNT));
    }
}
