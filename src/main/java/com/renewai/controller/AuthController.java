package com.renewai.controller;

import com.renewai.dto.LoginRequest;
import com.renewai.dto.LoginResponse;
import com.renewai.dto.RegistrationRequest;
import com.renewai.entity.Agent;
import com.renewai.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

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

    @Autowired
    private com.renewai.service.RateLimitingService rateLimitingService;
    
    /**
     * Agent login endpoint
     * POST /api/auth/login
     * @param loginRequest username and password
     * @return JWT token and agent details
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        io.github.bucket4j.Bucket bucket = rateLimitingService.resolveAuthBucket(request.getRemoteAddr());
        if (!bucket.tryConsume(1)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Too many login attempts. Please try again later.");
            return ResponseEntity.status(429).body(error);
        }

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
     * Agent registration endpoint
     * POST /api/auth/register or /api/auth/signup
     */
    @PostMapping({"/register", "/signup"})
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest authRequest, HttpServletRequest request) {
        io.github.bucket4j.Bucket bucket = rateLimitingService.resolveAuthBucket(request.getRemoteAddr());
        if (!bucket.tryConsume(1)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Too many registration attempts. Please try again later.");
            return ResponseEntity.status(429).body(error);
        }

        try {
            Agent agent = new Agent();
            agent.setUsername(authRequest.getUsername());
            agent.setEmail(authRequest.getEmail());
            agent.setPassword(authRequest.getPassword());
            agent.setFullName(authRequest.getFullName());
            agent.setPhoneNumber(authRequest.getPhoneNumber());

            Agent registeredAgent = authService.registerAgent(agent);
            
            // Generate token right after registration so they can be logged in automatically if needed
            // For now, just return success
            Map<String, String> response = new HashMap<>();
            response.put("message", "Registration successful");
            response.put("username", registeredAgent.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
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