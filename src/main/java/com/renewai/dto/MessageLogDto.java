package com.renewai.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * DTO for MessageLog entity
 * Includes retry mechanism fields for frontend display
 */
@Data
public class MessageLogDto {
    private Long id;
    private Long customerId;
    private Long policyId;
    private String clientName;
    private String policyNumber;
    private String reminderType;
    private String recipientPhone;
    private String messageContent;
    private String status;
    private String channel;
    private LocalDateTime sentAt;

    // Retry mechanism fields
    private Integer retryCount;
    private LocalDateTime lastAttemptAt;
    private String failureReason;

    // Computed helpers for the frontend
    private boolean canRetry;
    private boolean maxRetriesExhausted;
}
