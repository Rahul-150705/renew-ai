package com.renewai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for Policy response
 * Avoids circular reference issues with JPA entities
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDTO {
    
    private Long id;
    private String policyNumber;
    private String policyType;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private BigDecimal premium;
    private String premiumFrequency;
    private String description;
    private String status;
    private ClientBasicDTO client;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientBasicDTO {
        private Long id;
        private String fullName;
        private String email;
        private String phoneNumber;
    }
}