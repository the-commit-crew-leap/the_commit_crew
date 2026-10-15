package com.thecommitcrew.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thecommitcrew.application.OrderSettlementService;
import com.thecommitcrew.domain.enums.OrderSide;

@ExtendWith(MockitoExtension.class)
class ExecutionListenerTest {

    @Mock
    private OrderSettlementService settlementService;

    private ObjectMapper objectMapper;
    private ExecutionListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        listener = new ExecutionListener(objectMapper, settlementService);
    }

    @Test
    void onExecutionDeserializesMessageAndSettlesExecution() throws Exception {
        ExecutionEvent expected = new ExecutionEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC-1001",
                "AAPL",
                OrderSide.BUY,
                10,
                new BigDecimal("100.00"),
                new BigDecimal("101.00"),
                "SIM",
                Instant.parse("2026-01-01T12:00:00Z"));

        listener.onExecution(objectMapper.writeValueAsString(expected));

        verify(settlementService).settle(expected);
    }

    @Test
    void onExecutionSkipsUnreadableMessage() {
        listener.onExecution("not json");

        verifyNoInteractions(settlementService);
    }
}
