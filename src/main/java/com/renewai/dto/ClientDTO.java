package com.renewai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Client response
 * Avoids circular reference issues with JPA entities
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientDTO {
    
    private Long id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String address;
    private Long agentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}