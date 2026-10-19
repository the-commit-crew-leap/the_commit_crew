package com.thecommitcrew.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.persistence.mapper.OrderMapper;

@ExtendWith(MockitoExtension.class)
class PendingOrderRepublisherTest {

    private static final Duration REPUBLISH_AFTER = Duration.ofMinutes(5);

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderEventPublisher publisher;

    private PendingOrderRepublisher republisher;

    @BeforeEach
    void setUp() {
        republisher = new PendingOrderRepublisher(orderMapper, publisher, REPUBLISH_AFTER);
    }

    @Test
    void republishStaleOrdersQueriesConfiguredAgeAndBatchLimit() {
        OrderEvent first = event();
        OrderEvent second = event();
        when(orderMapper.findPendingOlderThan(300L, 100))
                .thenReturn(List.of(first, second));

        republisher.republishStaleOrders();

        verify(orderMapper).findPendingOlderThan(300L, 100);
        verify(publisher).publish(first);
        verify(publisher).publish(second);
    }

    @Test
    void republishStaleOrdersDoesNotPublishWhenNoOrdersAreStale() {
        when(orderMapper.findPendingOlderThan(300L, 100)).thenReturn(List.of());

        republisher.republishStaleOrders();

        verify(orderMapper).findPendingOlderThan(300L, 100);
        verifyNoInteractions(publisher);
    }

    private OrderEvent event() {
        return new OrderEvent(
                UUID.randomUUID(),
                "ACC-1001",
                "AAPL",
                OrderSide.BUY,
                10,
                new BigDecimal("100.00"),
                Instant.parse("2026-01-01T12:00:00Z"));
    }
}
