package com.renewai.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ConfirmRenewalRequest {
    
    @NotNull(message = "New start date is required")
    private LocalDate newStartDate;
    
    @NotNull(message = "New expiry date is required")
    private LocalDate newExpiryDate;
    
    private BigDecimal newPremium; // optional — if premium changed
    
    private String notes; // e.g. "Client called and confirmed"
    
    private String contactMethod; // CALL or WHATSAPP_REPLY
}
