package com.thecommitcrew.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final AuthServiceClient authServiceClient;

    public JwtAuthenticationFilter(AuthServiceClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                System.out.println("🔍 Validating token...");
                
                AuthServiceClient.ValidateResponse validated = authServiceClient.validateToken(token);
                System.out.println("🔍 Valid: " + validated.valid + ", Username: " + validated.username);
                
                if (validated.valid && validated.username != null) {
                    // Set request attribute for AuthCheckAspect
                    request.setAttribute("username", validated.username);
                    
                    // Create proper Spring Security Authentication
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        validated.username, null, Collections.emptyList()
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    System.out.println("✅ User authenticated: " + validated.username);
                }
            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        filterChain.doFilter(request, response);
    }
}