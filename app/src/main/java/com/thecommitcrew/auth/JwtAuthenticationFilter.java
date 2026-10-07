package com.thecommitcrew.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final AuthServiceClient authServiceClient;

    public JwtAuthenticationFilter(AuthServiceClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        // Get Authorization header: "Bearer eyJ..."
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7); // Remove "Bearer "
                
                // Call NestJS to validate token
                AuthServiceClient.ValidateResponse validated = authServiceClient.validateToken(token);
                
                if (validated.valid) {
                    // Token is valid - add username to request so controller can use it
                    request.setAttribute("username", validated.username);
                }
            } catch (Exception e) {
                // Log error but don't fail - let controller handle missing auth
            }
        }
        
        // Continue to next filter
        filterChain.doFilter(request, response);
    }
}