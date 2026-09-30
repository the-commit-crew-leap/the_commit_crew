package com.thecommitcrew.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.thecommitcrew.auth.JwtTokenProvider;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        // Mock auth: accept any username, generate token
        String username = request.getOrDefault("username", "testuser");
        String token = jwtTokenProvider.generateToken(username);
        return ResponseEntity.ok(Map.of("token", token));
    }
}