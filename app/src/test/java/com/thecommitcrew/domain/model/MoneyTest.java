package com.thecommitcrew.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class MoneyTest {
        

    @Test
    public void testDebit_NegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Money(new BigDecimal("-1000.00"));
        });
    }

    @Test
    public void testCredit_NegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Money(new BigDecimal("-1000.00"));
        });
    }
}
