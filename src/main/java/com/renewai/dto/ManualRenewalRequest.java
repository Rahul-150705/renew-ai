package com.renewai.dto;

import lombok.Data;

/**
 * Request DTO for manual renewal action
 * Agent provides notes after manually contacting the customer
 */
@Data
public class ManualRenewalRequest {
    private String notes;
    private boolean renewed;
}
