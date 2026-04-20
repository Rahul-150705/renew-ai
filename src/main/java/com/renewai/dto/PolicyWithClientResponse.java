package com.renewai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyWithClientResponse {

    // Policy Info
    private Long policyId;
    private String policyNumber;
    private String policyType;
    private String vehicleType;
    private String registrationNumber;
    private String insurerName;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private BigDecimal premium;
    private String premiumFrequency;
    private String policyDescription;
    private String policyStatus;
    private String renewalStatus;
    private String manualRenewalNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // PDF storage disabled
    // private boolean hasPdf;

    // Client Info
    private Long clientId;
    private String clientFullName;
    private String clientEmail;
    private String clientPhoneNumber;
    private String clientWhatsappNumber;
    private String clientAddress;
}