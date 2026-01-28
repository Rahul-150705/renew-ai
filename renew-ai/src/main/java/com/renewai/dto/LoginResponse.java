package com.renewai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for agent login response
 * Contains JWT token and agent details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    private String token;
    private String tokenType = "Bearer";
    private Long agentId;
    private String username;
    private String fullName;
    private String email;
    
    public LoginResponse(String token, Long agentId, String username, String fullName, String email) {
        this.token = token;
        this.agentId = agentId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
    }
}