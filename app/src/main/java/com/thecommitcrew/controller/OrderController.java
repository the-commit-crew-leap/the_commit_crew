package com.thecommitcrew.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import com.thecommitcrew.domain.dto.PlaceOrderRequestDTO;
import com.thecommitcrew.domain.dto.OrderResponseDTO;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.service.OrderService;


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
    
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    /**
     * Submit an order for processing.
     * 
     * The request is validated by Jakarta validation (@Valid).
     * The OrderService validates business rules and executes the order.
     * Exceptions are thrown for validation failures and caught by @ControllerAdvice.
     * 
     * POST /api/v1/orders
     * 
     * @param request the order request with validated fields
     * @return 201 Created with the created order response
     * @throws AccountNotFoundException if account doesn't exist
     * @throws InstrumentNotFoundException if instrument/symbol doesn't exist
     * @throws AccountNotActiveException if account is not active
     * @throws DuplicateOrderException if idempotency key already used
     * @throws InsufficientFundsException if account lacks funds for BUY order
     * @throws InsufficientHoldingsException if account lacks holdings for SELL order
     * @throws NegativePriceException if price is invalid
     */
    @PostMapping
    public ResponseEntity<OrderResponseDTO> submitOrder(@Valid @RequestBody PlaceOrderRequestDTO request) {
        // OrderService.placeOrder handles all validation and execution
        // Returns Order with status FILLED or REJECTED, or throws exception
        Order order = orderService.placeOrder(request);
        
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