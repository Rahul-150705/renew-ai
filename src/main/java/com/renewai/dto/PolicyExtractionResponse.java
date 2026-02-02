package com.renewai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for policy data extracted from PDF
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyExtractionResponse {
    
    // Client Information
    private String clientFullName;
    private String clientEmail;
    private String clientPhoneNumber;
    private String clientAddress;
    
    // Policy Information
    private String policyNumber;
    private String policyType;
    private String startDate;
    private String expiryDate;
    private String premium;
    private String premiumFrequency;
    private String policyDescription;
    
    // Extraction metadata
    private boolean success;
    private String message;
    private Double confidence; // AI confidence score (0-1)
}