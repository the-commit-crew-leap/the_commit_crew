package com.thecommitcrew.application;

import com.thecommitcrew.domain.dto.OrderUpdate;

/**
 * Where order lifecycle changes are announced to traders. Defined here so
 * the services don't depend on how updates are delivered; the API layer's
 * Server-Sent Events broadcaster is the implementation.
 */
public interface OrderUpdates {

    void publish(OrderUpdate update);
}