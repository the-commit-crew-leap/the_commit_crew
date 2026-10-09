package com.thecommitcrew.auth;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AuthServiceClient {
    private final RestTemplate restTemplate;
    private final String authServiceUrl; // http://auth:3000 from docker-compose

    public AuthServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.authServiceUrl = System.getenv().getOrDefault("AUTH_SERVICE_URL", "http://auth:3000");
    }

    /**
     * Validate JWT token by calling NestJS auth service
     * Sends: {token: "eyJ..."}
     * Gets back: {status: "SUCCESS", data: {username: "john", valid: true}}
     */
    public ValidateResponse validateToken(String token) {
        try {
            // Test basic connectivity first
            System.out.println("Attempting to reach auth service at: " + authServiceUrl);
            ResponseEntity<String> healthCheck = restTemplate.getForEntity(authServiceUrl + "/api/auth/health", String.class);
            System.out.println("Auth service health check: " + healthCheck.getBody());
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> payload = Map.of("token", token);
            String jsonPayload = mapper.writeValueAsString(payload);
            
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                authServiceUrl + "/api/auth/validate",
                request,
                String.class
            );
            return parseValidateResponse(response.getBody());
        } catch (Exception e) {
            return new ValidateResponse(false, null);
        }
    }

    private ValidateResponse parseValidateResponse(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.readTree(json);
        
        if ("SUCCESS".equals(root.get("status").asText())) {
            var data = root.get("data");
            return new ValidateResponse(
                data.get("valid").asBoolean(),
                data.get("username").asText(),
                data.get("accountId") != null ? data.get("accountId").asText() : null,
                data.get("role") != null ? data.get("role").asText() : null
            );
        }
        return new ValidateResponse(false, null);
    }


    public static class ValidateResponse {

        public final boolean valid;
        public final String username;
        public final String accountId;
        public final String role;

        public ValidateResponse(boolean valid, String username) {
            this.valid = valid;
            this.username = username;
            this.accountId = null;
            this.role = null;
        }

        public ValidateResponse(boolean valid, String username, String accountId, String role) {
            this.valid = valid;
            this.username = username;
            this.accountId = accountId;
            this.role = role;
        }
    }
}