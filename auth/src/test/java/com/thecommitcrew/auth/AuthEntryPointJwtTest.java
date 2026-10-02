package com.thecommitcrew.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.AuthenticationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class AuthEntryPointJwtTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private AuthenticationException authException;

    private AuthEntryPointJwt authEntryPointJwt;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() throws IOException, ServletException {
        authEntryPointJwt = new AuthEntryPointJwt();
        outputStream = new ByteArrayOutputStream();
        
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/protected");
        when(authException.getMessage()).thenReturn("Invalid token");
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                outputStream.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                // Not used in this test - WriteListener is not required for unit testing response output
            }
        });
    }

    @Test
    void commence_shouldSetResponseStatusToUnauthorized() throws IOException, ServletException {
        authEntryPointJwt.commence(request, response, authException);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void commence_shouldSetContentTypeToJson() throws IOException, ServletException {
        authEntryPointJwt.commence(request, response, authException);
        verify(response).setContentType("application/json");
    }

    @Test
    void commence_shouldWriteErrorResponseDTO() throws IOException, ServletException {
        authEntryPointJwt.commence(request, response, authException);
        
        String output = outputStream.toString();
        assertThat(output).contains("Unauthorized");
    }

    @Test
    void commence_shouldLogErrorMessage() throws IOException, ServletException {
        authEntryPointJwt.commence(request, response, authException);
        
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
    }
}