package com.thecommitcrew.auth;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(authServiceClient);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerToken_setsRequestAttributesAndAuthentication()
            throws Exception {
        MockHttpServletRequest request = requestWithHeader("Bearer jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authServiceClient.validateToken("jwt-token"))
                .thenReturn(new AuthServiceClient.ValidateResponse(
                        true,
                        "jane",
                        "ACC-1001",
                        "TRADER"
                ));

        filter.doFilterInternal(request, response, filterChain);

        assertEquals("jane", request.getAttribute("username"));
        assertEquals("ACC-1001", request.getAttribute("accountId"));
        assertEquals("TRADER", request.getAttribute("role"));

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        assertTrue(authentication.isAuthenticated());
        assertEquals("jane", authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().isEmpty());

        verify(authServiceClient).validateToken("jwt-token");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidBearerToken_doesNotSetAuthentication()
            throws Exception {
        MockHttpServletRequest request = requestWithHeader("Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authServiceClient.validateToken("invalid-token"))
                .thenReturn(new AuthServiceClient.ValidateResponse(
                        false,
                        null
                ));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(request.getAttribute("username"));
        assertNull(request.getAttribute("accountId"));
        assertNull(request.getAttribute("role"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verify(authServiceClient).validateToken("invalid-token");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validTokenWithoutUsername_doesNotSetAuthentication()
            throws Exception {
        MockHttpServletRequest request = requestWithHeader("Bearer jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authServiceClient.validateToken("jwt-token"))
                .thenReturn(new AuthServiceClient.ValidateResponse(
                        true,
                        null,
                        "ACC-1001",
                        "TRADER"
                ));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute("username"));

        verify(authServiceClient).validateToken("jwt-token");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingAuthorizationHeader_skipsValidation()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(authServiceClient, never()).validateToken(
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void nonBearerAuthorizationHeader_skipsValidation()
            throws Exception {
        MockHttpServletRequest request =
                requestWithHeader("Basic credentials");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(authServiceClient, never()).validateToken(
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void bearerPrefixWithEmptyToken_passesEmptyTokenToClient()
            throws Exception {
        MockHttpServletRequest request = requestWithHeader("Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authServiceClient.validateToken(""))
                .thenReturn(new AuthServiceClient.ValidateResponse(
                        false,
                        null
                ));

        filter.doFilterInternal(request, response, filterChain);

        verify(authServiceClient).validateToken("");
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void authClientException_isSwallowedAndFilterChainContinues()
            throws Exception {
        MockHttpServletRequest request =
                requestWithHeader("Bearer broken-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RuntimeException failure =
                new RuntimeException("Auth service unavailable");

        when(authServiceClient.validateToken("broken-token"))
                .thenThrow(failure);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute("username"));
        assertNull(request.getAttribute("accountId"));
        assertNull(request.getAttribute("role"));

        verify(authServiceClient).validateToken("broken-token");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validTokenWithNullOptionalFields_setsNullOptionalAttributes()
            throws Exception {
        MockHttpServletRequest request = requestWithHeader("Bearer jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authServiceClient.validateToken("jwt-token"))
                .thenReturn(new AuthServiceClient.ValidateResponse(
                        true,
                        "jane",
                        null,
                        null
                ));

        filter.doFilterInternal(request, response, filterChain);

        assertEquals("jane", request.getAttribute("username"));
        assertNull(request.getAttribute("accountId"));
        assertNull(request.getAttribute("role"));

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        assertSame("jane", authentication.getPrincipal());
        verify(filterChain).doFilter(request, response);
    }

    private MockHttpServletRequest requestWithHeader(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", authorization);
        return request;
    }
}