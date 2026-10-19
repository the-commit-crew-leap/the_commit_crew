package com.thecommitcrew.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthCheckAspectTest {

    private final AuthCheckAspect aspect = new AuthCheckAspect();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void checkAuth_withoutRequestContext_returnsUnauthorized() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        Object result = aspect.checkAuth(joinPoint);

        assertUnauthorized(
                result,
                "Unauthorized - No request context"
        );
        verify(joinPoint, never()).proceed();
    }

    @Test
    void checkAuth_withoutUsername_returnsUnauthorized() throws Throwable {
        MockHttpServletRequest request = requestWithAttributes(null, "ACC-1001");
        setRequestContext(request);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        Object result = aspect.checkAuth(joinPoint);

        assertUnauthorized(
                result,
                "Unauthorized - Invalid or missing token"
        );
        verify(joinPoint, never()).proceed();
    }

    @Test
    void checkAuth_withoutAccountId_returnsUnauthorized() throws Throwable {
        MockHttpServletRequest request = requestWithAttributes("jane", null);
        setRequestContext(request);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        Object result = aspect.checkAuth(joinPoint);

        assertUnauthorized(
                result,
                "Unauthorized - No account linked to user"
        );
        verify(joinPoint, never()).proceed();
    }

    @Test
    void checkAuth_withUsernameAndAccountId_proceeds() throws Throwable {
        MockHttpServletRequest request = requestWithAttributes("jane", "ACC-1001");
        setRequestContext(request);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Object expectedResult = new Object();

        when(joinPoint.proceed()).thenReturn(expectedResult);

        Object result = aspect.checkAuth(joinPoint);

        assertSame(expectedResult, result);
        verify(joinPoint).proceed();
    }

    private MockHttpServletRequest requestWithAttributes(String username, String accountId) {
        MockHttpServletRequest request = new MockHttpServletRequest();

        if (username != null) {
            request.setAttribute("username", username);
        }

        if (accountId != null) {
            request.setAttribute("accountId", accountId);
        }

        return request;
    }

    private void setRequestContext(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request)
        );
    }

    private void assertUnauthorized(Object result, String message) {
        ResponseEntity<?> response = (ResponseEntity<?>) result;

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        ErrorResponse body = (ErrorResponse) response.getBody();

        assertEquals("ERROR", body.getStatus());
        assertEquals(message, body.getMessage());
        assertEquals(null, body.getData());
    }
}