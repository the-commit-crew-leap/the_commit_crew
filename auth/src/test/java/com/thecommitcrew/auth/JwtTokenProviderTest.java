package com.thecommitcrew.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    private String testSecret;
    private long testExpiration;

    @BeforeEach
    void setUp() {
        testSecret = "mySecretKeyThatIsLongEnoughForHS256SigningAlgorithm123";
        testExpiration = 3600000; // 1 hour
        
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", testExpiration);
    }

    @Test
    void generateToken_shouldCreateValidToken() {
        String username = "testuser";
        
        String token = jwtTokenProvider.generateToken(username);
        
        assertThat(token).isNotNull();
        assertThat(token).isNotBlank();
    }

    @Test
    void validateToken_shouldReturnTrueForValidToken() {
        String username = "testuser";
        String token = jwtTokenProvider.generateToken(username);
        
        boolean isValid = jwtTokenProvider.validateToken(token);
        
        assertThat(isValid).isTrue();
    }

    @Test
    void validateToken_shouldReturnFalseForInvalidToken() {
        String invalidToken = "invalid.token.format";
        
        boolean isValid = jwtTokenProvider.validateToken(invalidToken);
        
        assertThat(isValid).isFalse();
    }

    @Test
    void validateToken_shouldReturnFalseForExpiredToken() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", -1000);
        String token = jwtTokenProvider.generateToken("testuser");
        
        boolean isValid = jwtTokenProvider.validateToken(token);
        
        assertThat(isValid).isFalse();
    }

    @Test
    void validateToken_shouldReturnFalseForNullToken() {
        boolean isValid = jwtTokenProvider.validateToken(null);
        
        assertThat(isValid).isFalse();
    }

    @Test
    void getUserNameFromJwtToken_shouldExtractUsernameFromToken() {
        String username = "testuser";
        String token = jwtTokenProvider.generateToken(username);
        
        String extractedUsername = jwtTokenProvider.getUserNameFromJwtToken(token);
        
        assertThat(extractedUsername).isEqualTo(username);
    }

    @Test
    void getUserNameFromJwtToken_shouldWorkWithDifferentUsernames() {
        String[] usernames = {"alice", "bob", "charlie123"};
        
        for (String username : usernames) {
            String token = jwtTokenProvider.generateToken(username);
            String extractedUsername = jwtTokenProvider.getUserNameFromJwtToken(token);
            assertThat(extractedUsername).isEqualTo(username);
        }
    }

    @Test
    void getExpirationTime_shouldReturnConfiguredExpirationTime() {
        long expirationTime = jwtTokenProvider.getExpirationTime();
        
        assertThat(expirationTime).isEqualTo(testExpiration);
    }

    @Test
    void generateToken_shouldIncludeExpirationClaim() {
        String token = jwtTokenProvider.generateToken("testuser");
        
        SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
        Date expiration = (Date) Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
        
        assertThat(expiration).isNotNull();
        assertThat(expiration.getTime()).isGreaterThan(System.currentTimeMillis());
    }
}