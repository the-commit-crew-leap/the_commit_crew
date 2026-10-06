package com.thecommitcrew.auth;

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
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            
            String payload = String.format("{\"token\": \"%s\"}", token);
            HttpEntity<String> request = new HttpEntity<>(payload, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                authServiceUrl + "/api/auth/validate",
                request,
                String.class
            );
            
            // Parse response and return validation result
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
                data.get("username").asText()
            );
        }
        return new ValidateResponse(false, null);
    }

    public static class ValidateResponse {
        public final boolean valid;
        public final String username;

        public ValidateResponse(boolean valid, String username) {
            this.valid = valid;
            this.username = username;
        }
    }
}