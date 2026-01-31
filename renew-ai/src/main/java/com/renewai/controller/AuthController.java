package com.renewai.controller;

import com.renewai.dto.LoginRequest;
import com.renewai.dto.LoginResponse;
import com.renewai.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication Controller
 * Handles agent login and registration
 * PUBLIC ENDPOINT - No JWT required
 * FIXED: Removed @CrossOrigin as CORS is now configured globally in SecurityConfig
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private AuthService authService;
    
    /**
     * Agent login endpoint
     * POST /api/auth/login
     * @param loginRequest username and password
     * @return JWT token and agent details
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            LoginResponse response = authService.login(loginRequest);
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }
    
    /**
     * Health check endpoint
     * GET /api/auth/health
     * @return status message
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Renew AI");
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }
}