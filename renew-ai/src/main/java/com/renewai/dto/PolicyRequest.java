// package com.renewai.dto;

// import jakarta.validation.constraints.*;
// import lombok.AllArgsConstructor;
// import lombok.Data;
// import lombok.NoArgsConstructor;

// import java.math.BigDecimal;
// import java.time.LocalDate;

// /**
//  * DTO for creating a new insurance policy
//  */
// @Data
// @NoArgsConstructor
// @AllArgsConstructor
// public class PolicyRequest {
    
//     @NotNull(message = "Client ID is required")
//     private Long clientId;
    
//     @NotBlank(message = "Policy number is required")
//     private String policyNumber;
    
//     @NotBlank(message = "Policy type is required")
//     private String policyType;
    
//     @NotNull(message = "Start date is required")
//     private LocalDate startDate;
    
//     @NotNull(message = "Expiry date is required")
//     @Future(message = "Expiry date must be in the future")
//     private LocalDate expiryDate;
    
//     @NotNull(message = "Premium amount is required")
//     @DecimalMin(value = "0.01", message = "Premium must be greater than 0")
//     private BigDecimal premium;
    
//     private String premiumFrequency = "YEARLY";
    
//     private String description;
// }