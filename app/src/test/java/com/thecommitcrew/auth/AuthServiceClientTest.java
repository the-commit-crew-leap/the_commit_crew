package com.thecommitcrew.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.thecommitcrew.auth.AuthServiceClient.ValidateResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthServiceClientTest {

    private static final String AUTH_SERVICE_URL = "http://auth:3000";
    private static final String HEALTH_URL = AUTH_SERVICE_URL + "/api/auth/health";
    private static final String VALIDATE_URL = AUTH_SERVICE_URL + "/api/auth/validate";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private AuthServiceClient authServiceClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        authServiceClient = new AuthServiceClient(restTemplate);
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void validateToken_successResponse_returnsFullValidationResponse() {
        stubHealthCheck();

        server.expect(requestTo(VALIDATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                ))
                .andExpect(content().json("""
                        {
                          "token": "jwt-token"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "status": "SUCCESS",
                          "data": {
                            "valid": true,
                            "username": "jane",
                            "accountId": "ACC-1001",
                            "role": "TRADER"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AuthServiceClient.ValidateResponse response = authServiceClient.validateToken("jwt-token");

        assertTrue(response.valid);
        assertEquals("jane", response.username);
        assertEquals("ACC-1001", response.accountId);
        assertEquals("TRADER", response.role);
    }

    @Test
    void validateToken_successResponseWithoutOptionalFields_returnsNullOptionalFields() {
        stubHealthCheck();

        server.expect(requestTo(VALIDATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "status": "SUCCESS",
                          "data": {
                            "valid": true,
                            "username": "jane"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AuthServiceClient.ValidateResponse response = authServiceClient.validateToken("jwt-token");

        assertTrue(response.valid);
        assertEquals("jane", response.username);
        assertNull(response.accountId);
        assertNull(response.role);
    }

    @Test
    void validateToken_invalidResponse_returnsInvalidResponse() {
        stubHealthCheck();

        server.expect(requestTo(VALIDATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "status": "ERROR",
                          "data": null
                        }
                        """, MediaType.APPLICATION_JSON));

        AuthServiceClient.ValidateResponse response = authServiceClient.validateToken("invalid-token");

        assertFalse(response.valid);
        assertNull(response.username);
        assertNull(response.accountId);
        assertNull(response.role);
    }

    @Test
    void validateToken_invalidSuccessfulValidation_returnsInvalidResponse() {
        stubHealthCheck();

        server.expect(requestTo(VALIDATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "status": "SUCCESS",
                          "data": {
                            "valid": false,
                            "username": "jane",
                            "accountId": "ACC-1001",
                            "role": "TRADER"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AuthServiceClient.ValidateResponse response = authServiceClient.validateToken("expired-token");

        assertFalse(response.valid);
        assertEquals("jane", response.username);
        assertEquals("ACC-1001", response.accountId);
        assertEquals("TRADER", response.role);
    }

    @Test
    void validateToken_healthCheckFailure_returnsInvalidResponse() {
        server.expect(requestTo(HEALTH_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new RuntimeException("Auth service unavailable");
                });

        AuthServiceClient.ValidateResponse response = authServiceClient.validateToken("jwt-token");

        assertFalse(response.valid);
        assertNull(response.username);
        assertNull(response.accountId);
        assertNull(response.role);
    }

    @Test
    void validateToken_validationRequestFailure_returnsInvalidResponse() {
        stubHealthCheck();

        server.expect(requestTo(VALIDATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {throw new RuntimeException("Validation request failed");});

        AuthServiceClient.ValidateResponse response = authServiceClient.validateToken("jwt-token");

        assertFalse(response.valid);
        assertNull(response.username);
        assertNull(response.accountId);
        assertNull(response.role);
    }

    @Test
    void validateToken_malformedJson_returnsInvalidResponse() {
        stubHealthCheck();

        server.expect(requestTo(VALIDATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{ malformed-json",
                        MediaType.APPLICATION_JSON
                ));

        AuthServiceClient.ValidateResponse response = authServiceClient.validateToken("jwt-token");

        assertFalse(response.valid);
        assertNull(response.username);
        assertNull(response.accountId);
        assertNull(response.role);
    }

    @Test
    void validateResponse_twoArgumentConstructor_setsOptionalFieldsToNull() {
        AuthServiceClient.ValidateResponse response = new ValidateResponse(true, "jane");

        assertTrue(response.valid);
        assertEquals("jane", response.username);
        assertNull(response.accountId);
        assertNull(response.role);
    }

    private void stubHealthCheck() {
        server.expect(requestTo(HEALTH_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":\"UP\"}",
                        MediaType.APPLICATION_JSON
                ));
    }
}