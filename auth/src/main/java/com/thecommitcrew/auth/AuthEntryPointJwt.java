package com.thecommitcrew.auth;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thecommitcrew.auth.dto.ErrorResponseDTO;

/**
 * Handles unauthorized access - returns JSON 401 response
 */
@Component
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(AuthEntryPointJwt.class);

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {

        logger.error("Unauthorized error: {} - {} {}", 
                authException.getMessage(),
                request.getMethod(),
                request.getRequestURI());

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        final ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(
                response.getOutputStream(),
                new ErrorResponseDTO("Error: Unauthorized", null)
        );
    }
}