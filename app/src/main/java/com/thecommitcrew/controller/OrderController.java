package com.thecommitcrew.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.thecommitcrew.domain.dto.PlaceOrderRequestDTO;
import com.thecommitcrew.domain.exception.AccountNotActiveException;
import com.thecommitcrew.domain.exception.DuplicateOrderException;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.domain.exception.InsufficientFundsException;
import com.thecommitcrew.domain.exception.InsufficientHoldingsException;
import com.thecommitcrew.domain.exception.NegativePriceException;
import com.thecommitcrew.auth.CheckAuth;
import com.thecommitcrew.domain.dto.OrderResponseDTO;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.service.OrderService;
import com.thecommitcrew.messaging.OrderEventPublisher;
import com.thecommitcrew.messaging.OrderEvent;


/**
 * OrderController handles HTTP requests for order operations.
 * Delegates all business logic to OrderService which handles validation and execution.
 * Exception handling is managed by a centralized @ControllerAdvice exception handler.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {
    
    private final OrderService orderService;
    private final OrderEventPublisher orderEventPublisher;
    
    public OrderController(OrderService orderService, OrderEventPublisher orderEventPublisher) {
        this.orderService = orderService;
        this.orderEventPublisher = orderEventPublisher;
    }
    
/**
 * Submit an order for processing.
 * 
 * Requires JWT authentication token in Authorization header.
 * The request is validated by Jakarta validation (@Valid).
 * The OrderService validates business rules and executes the order.
 * Exceptions are thrown for validation failures and caught by @ControllerAdvice.
 * 
 * POST /api/v1/orders
 * Header: Authorization: Bearer eyJ...
 * 
 * @param request the order request with validated fields
 * @param httpRequest the HTTP request (contains authenticated username)
 * @return 201 Created with the created order response
 * @throws UnauthorizedException if token is missing or invalid
 * @throws AccountNotFoundException if account doesn't exist
 * @throws InstrumentNotFoundException if instrument/symbol doesn't exist
 * @throws AccountNotActiveException if account is not active
 * @throws DuplicateOrderException if idempotency key already used
 * @throws InsufficientFundsException if account lacks funds for BUY order
 * @throws InsufficientHoldingsException if account lacks holdings for SELL order
 * @throws NegativePriceException if price is invalid
 */
@PostMapping
@CheckAuth
public ResponseEntity<OrderResponseDTO> submitOrder(
        @Valid @RequestBody PlaceOrderRequestDTO request,
        HttpServletRequest httpRequest) {
    
    // Get authenticated username from JWT filter
    String username = (String) httpRequest.getAttribute("username");
    
    // @CheckAuth aspect already validates username is present
    // but explicit check adds safety and documentation
    if (username == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    
    // OrderService.placeOrder handles all validation and execution
    // Returns Order with status FILLED or REJECTED, or throws exception
    Order order = orderService.placeOrder(request);
    
    // Send order to execution venue (Kafka)
    if (order.getStatus().toString().equals("NEW")) {
        OrderEvent event = new OrderEvent(
            order.getId(),
            order.getAccountId(),
            order.getSymbol(),
            order.getSide(),
            (int) order.getQuantity(),
            order.getPrice(),
            Instant.now()
        );
        orderEventPublisher.publish(event);
    }

    OrderResponseDTO response = mapToResponse(order);
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(response);
}

    /**
     * Cancel a working order.
     * 
     * Only NEW orders can be cancelled. Other statuses will throw IllegalStateException.
     * 
     * DELETE /api/v1/orders/{id}
     * 
     * @param id the order UUID as a string
     * @return 204 No Content on success
     * @throws IllegalArgumentException if id is not a valid UUID
     * @throws IllegalArgumentException if order not found
     * @throws IllegalStateException if order status is not NEW
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelOrder(@PathVariable String id) {
        // Parse and validate UUID format
        UUID orderId = UUID.fromString(id);
        
        // Cancel the order (throws exception if not in NEW status)
        orderService.cancelOrder(orderId);
        
        // Return 204 No Content
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Map Order domain model to OrderResponseDTO
     */
    private OrderResponseDTO mapToResponse(Order order) {
        return new OrderResponseDTO(
            order.getId(),
            order.getAccountId(),
            order.getSymbol(),
            order.getSide(),
            order.getQuantity(),
            order.getPrice(),
            order.getStatus(),
            order.getCreatedOn()
        );
    }
}