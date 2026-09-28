package com.thecommitcrew.domain.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ErrorResponseDTOTest {
    
    @Test
    void testErrorResponseCreation() {
        String errorCode = "ORDER_REJECTED";
        String message = "Insufficient funds in account";
        
        ErrorResponseDTO response = new ErrorResponseDTO(
            errorCode,
            message
        );
        
        assertNotNull(response);
        assertEquals(errorCode, response.errorCode());
        assertEquals(message, response.message());
    }
    
    @Test
    void testErrorResponseFields() {
        String errorCode = "INSUFFICIENT_BALANCE";
        String message = "Account does not have enough cash to complete this order";
        
        ErrorResponseDTO response = new ErrorResponseDTO(
            errorCode,
            message
        );
        
        assertEquals(errorCode, response.errorCode());
        assertEquals(message, response.message());
    }
}
