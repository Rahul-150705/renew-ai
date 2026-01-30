package com.renewai.service;

import com.renewai.dto.LoginRequest;
import com.renewai.dto.LoginResponse;
import com.renewai.entity.Agent;
import com.renewai.repository.AgentRepository;
import com.renewai.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service for agent authentication
 * Handles login and JWT token generation
 */
@Service
public class AuthService {
    
    @Autowired
    private AgentRepository agentRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * Authenticate agent and generate JWT token
     * @param loginRequest login credentials
     * @return LoginResponse with JWT token
     * @throws RuntimeException if credentials are invalid
     */
    public LoginResponse login(LoginRequest loginRequest) {
        // Find agent by username
        Agent agent = agentRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));
        
        // Verify password using BCrypt
        if (!passwordEncoder.matches(loginRequest.getPassword(), agent.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }
        
        // Check if agent is active
        if (!agent.getActive()) {
            throw new RuntimeException("Agent account is inactive");
        }
        
        // Generate JWT token
        String token = jwtUtil.generateToken(agent.getUsername(), agent.getId());
        
        // Return login response
        return new LoginResponse(
            token,
            agent.getId(),
            agent.getUsername(),
            agent.getFullName(),
            agent.getEmail()
        );
    }
    
    /**
     * Register a new agent
     * Note: In production, this should be restricted to admin users
     * @param agent agent to register
     * @return registered agent
     */
    public Agent registerAgent(Agent agent) {
        // Check if username already exists
        if (agentRepository.existsByUsername(agent.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        
        // Check if email already exists
        if (agentRepository.existsByEmail(agent.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        
        // Hash password using BCrypt
        agent.setPassword(passwordEncoder.encode(agent.getPassword()));
        agent.setActive(true);
        
        // Save and return agent
        return agentRepository.save(agent);
    }
}