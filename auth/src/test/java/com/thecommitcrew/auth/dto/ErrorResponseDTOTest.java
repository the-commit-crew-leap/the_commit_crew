package com.thecommitcrew.auth.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseDTOTest {

    @Test
    void constructor_shouldSetMessageAndDetails() {
        String message = "Error occurred";
        String details = "Additional error details";
        
        ErrorResponseDTO dto = new ErrorResponseDTO(message, details);
        
        assertThat(dto.getMessage()).isEqualTo(message);
        assertThat(dto.getDetails()).isEqualTo(details);
    }

    @Test
    void constructor_shouldAllowNullDetails() {
        String message = "Error occurred";
        
        ErrorResponseDTO dto = new ErrorResponseDTO(message, null);
        
        assertThat(dto.getMessage()).isEqualTo(message);
        assertThat(dto.getDetails()).isNull();
    }

    @Test
    void setMessage_shouldUpdateMessage() {
        ErrorResponseDTO dto = new ErrorResponseDTO("Initial", "details");
        dto.setMessage("Updated");
        
        assertThat(dto.getMessage()).isEqualTo("Updated");
    }

    @Test
    void setDetails_shouldUpdateDetails() {
        ErrorResponseDTO dto = new ErrorResponseDTO("message", "Initial");
        dto.setDetails("Updated");
        
        assertThat(dto.getDetails()).isEqualTo("Updated");
    }

    @Test
    void gettersAndSetters_shouldWorkCorrectly() {
        ErrorResponseDTO dto = new ErrorResponseDTO("msg", "details");
        
        assertThat(dto.getMessage()).isEqualTo("msg");
        assertThat(dto.getDetails()).isEqualTo("details");
        
        dto.setMessage("new message");
        dto.setDetails("new details");
        
        assertThat(dto.getMessage()).isEqualTo("new message");
        assertThat(dto.getDetails()).isEqualTo("new details");
    }

    @Test
    void constructor_shouldAllowNullMessage() {
        ErrorResponseDTO dto = new ErrorResponseDTO(null, "details");
        
        assertThat(dto.getMessage()).isNull();
        assertThat(dto.getDetails()).isEqualTo("details");
    }

    @Test
    void constructor_shouldAllowBothNull() {
        ErrorResponseDTO dto = new ErrorResponseDTO(null, null);
        
        assertThat(dto.getMessage()).isNull();
        assertThat(dto.getDetails()).isNull();
    }

    @Test
    void errorResponseDTO_shouldBeSerializable() {
        ErrorResponseDTO dto = new ErrorResponseDTO("Error", "Details");
        
        assertThat(dto).isNotNull();
        assertThat(dto.getMessage()).isNotNull();
        assertThat(dto.getDetails()).isNotNull();
    }
}