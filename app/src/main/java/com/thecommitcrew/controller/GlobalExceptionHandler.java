package com.thecommitcrew.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.thecommitcrew.domain.dto.ErrorResponseDTO;
import com.thecommitcrew.domain.exception.AccountNotActiveException;
import com.thecommitcrew.domain.exception.AccountNotFoundException;
import com.thecommitcrew.domain.exception.DuplicateOrderException;
import com.thecommitcrew.domain.exception.InsufficientFundsException;
import com.thecommitcrew.domain.exception.InsufficientHoldingsException;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.domain.exception.NegativePriceException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    // 404 Not Found
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccountNotFound(AccountNotFoundException e) {
        ErrorResponseDTO error = new ErrorResponseDTO("ACCOUNT_NOT_FOUND", e.getMessage());
        return ResponseEntity.status(404).body(error);
    }

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleInstrumentNotFound(InstrumentNotFoundException e) {
        ErrorResponseDTO error = new ErrorResponseDTO("INSTRUMENT_NOT_FOUND", e.getMessage());
        return ResponseEntity.status(404).body(error);
    }

    // 422 Unprocessable Entity (Business Rule Violations)
    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccountNotActive(AccountNotActiveException e) {
        ErrorResponseDTO error = new ErrorResponseDTO("ACCOUNT_NOT_ACTIVE", e.getMessage());
        return ResponseEntity.status(422).body(error);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponseDTO> handleInsufficientFunds(InsufficientFundsException e) {
        ErrorResponseDTO error = new ErrorResponseDTO("INSUFFICIENT_FUNDS", e.getMessage());
        return ResponseEntity.status(422).body(error);
    }

    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<ErrorResponseDTO> handleInsufficientHoldings(InsufficientHoldingsException e) {
        ErrorResponseDTO error = new ErrorResponseDTO("INSUFFICIENT_HOLDINGS", e.getMessage());
        return ResponseEntity.status(422).body(error);
    }

    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponseDTO> handleDuplicateOrder(DuplicateOrderException e) {
        ErrorResponseDTO error = new ErrorResponseDTO("DUPLICATE_ORDER", e.getMessage());
        return ResponseEntity.status(422).body(error);
    }

    // 400 Bad Request
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgument(IllegalArgumentException e) {
        ErrorResponseDTO error = new ErrorResponseDTO("INVALID_REQUEST", e.getMessage());
        return ResponseEntity.status(400).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalState(IllegalStateException e) {
        ErrorResponseDTO error = new ErrorResponseDTO("INVALID_STATE", e.getMessage());
        return ResponseEntity.status(400).body(error);
    }

    @ExceptionHandler(NegativePriceException.class)
    public ResponseEntity<ErrorResponseDTO> handleNegativePrice(NegativePriceException e) {
        ErrorResponseDTO error = new ErrorResponseDTO("INVALID_PRICE", e.getMessage());
        return ResponseEntity.status(400).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst()
            .orElse("Invalid request body");
        ErrorResponseDTO error = new ErrorResponseDTO("VALIDATION_ERROR", message);
        return ResponseEntity.status(400).body(error);
    }

    // 500 Internal Server Error (Fallback)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception e) {
        logger.error("Unhandled exception:", e);
        ErrorResponseDTO error = new ErrorResponseDTO("INTERNAL_SERVER_ERROR", "An unexpected error occurred");
        return ResponseEntity.status(500).body(error);
    }
}