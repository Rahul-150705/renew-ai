package com.renewai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for policy response with client information
 * This prevents circular JSON serialization and includes all needed data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyWithClientResponse {
    
    // Policy Info
    private Long policyId;
    private String policyNumber;
    private String policyType;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private BigDecimal premium;
    private String premiumFrequency;
    private String policyDescription;
    private String policyStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Client Info
    private Long clientId;
    private String clientFullName;
    private String clientEmail;
    private String clientPhoneNumber;
    private String clientAddress;
}