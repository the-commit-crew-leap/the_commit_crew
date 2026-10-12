package com.thecommitcrew.auth;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuthCheckAspect {
    
    @Around("@annotation(com.thecommitcrew.auth.CheckAuth)")
    public Object checkAuth(ProceedingJoinPoint joinPoint) throws Throwable {
        
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("ERROR", "Unauthorized - No request context", null));
        }
        
        HttpServletRequest request = attributes.getRequest();
        
        String username = (String) request.getAttribute("username");
        String accountId = (String) request.getAttribute("accountId");
        
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("ERROR", "Unauthorized - Invalid or missing token", null));
        }

        if (accountId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("ERROR", "Unauthorized - No account linked to user", null));
        }
        
        return joinPoint.proceed();
    }
}  