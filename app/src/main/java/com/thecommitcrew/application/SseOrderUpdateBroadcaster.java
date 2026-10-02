package com.thecommitcrew.application;

import com.thecommitcrew.domain.dto.OrderUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Temporary implementation that just logs order updates.
 * Will be implemented fully once we add UI.
 */
@Component
public class SseOrderUpdateBroadcaster implements OrderUpdates {
    
    private static final Logger log = LoggerFactory.getLogger(SseOrderUpdateBroadcaster.class);
    
    @Override
    public void publish(OrderUpdate update) {
        log.info("Order {} {}: {} - {}", 
                update.orderId(), 
                update.status(), 
                update.symbol(), 
                update.message());
    }
}