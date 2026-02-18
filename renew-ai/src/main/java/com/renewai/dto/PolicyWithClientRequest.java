package com.renewai.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyWithClientRequest {

    // Client Details
    @NotBlank(message = "Client full name is required")
    private String clientFullName;

    @NotBlank(message = "Client email is required")
    @Email(message = "Invalid email format")
    private String clientEmail;

    @NotBlank(message = "Client phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String clientPhoneNumber;

    private String clientAddress;

    // Policy Details
    @NotBlank(message = "Policy number is required")
    private String policyNumber;

    @NotBlank(message = "Policy type is required")
    private String policyType;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "Expiry date is required")
    @FutureOrPresent(message = "Expiry date must be today or in the future")
    private LocalDate expiryDate;

    @NotNull(message = "Premium amount is required")
    @DecimalMin(value = "0.01", message = "Premium must be greater than 0")
    private BigDecimal premium;

    private String premiumFrequency = "YEARLY";

    private String policyDescription;

    // Optional: path to stored PDF file (set after PDF extraction)
    private String pdfFilePath;
}