package com.renewai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * MessageLog Entity - Tracks all renewal reminder messages sent
 * Prevents duplicate messages using composite unique constraint
 * Supports retry mechanism with max 3 attempts
 */
@Entity
@Table(
    name = "message_logs",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"policy_id", "reminder_type", "channel"},
        name = "uk_policy_reminder_channel"
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageLog {

    public static final int MAX_RETRY_COUNT = 3;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Direct reference to customer for easier querying
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Client customer;
    
    // Reference to the policy for which message was sent
    // FIXED: Added @JsonIgnore to prevent circular serialization
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;
    
    // Type of reminder: SEVEN_DAYS or THREE_DAYS
    @Column(nullable = false, length = 20)
    private String reminderType;
    
    // Message type: SMS or WHATSAPP
    @Column(nullable = false, length = 20)
    private String channel = "SMS";
    
    // Phone number where message was sent
    @Column(nullable = false, length = 15)
    private String recipientPhone;
    
    // The actual message content sent
    @Column(nullable = false, length = 500)
    private String messageContent;
    
    // Message delivery status: SENT, FAILED, PENDING
    @Column(nullable = false, length = 20)
    private String status = "PENDING";
    
    // External message ID from Twilio (if applicable)
    @Column(length = 100)
    private String externalMessageId;
    
    // Error message if sending failed (legacy field, kept for backward compat)
    @Column(length = 500)
    private String errorMessage;

    // ===== RETRY MECHANISM FIELDS =====

    // Number of retry attempts made (max 3)
    @Column(nullable = false)
    private Integer retryCount = 0;

    // Timestamp of the last retry attempt
    @Column
    private LocalDateTime lastAttemptAt;

    // Detailed failure reason from the last attempt
    @Column(length = 1000)
    private String failureReason;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime sentAt;
    
    /**
     * Constructor for easy creation during message sending
     */
    public MessageLog(Policy policy, String reminderType, String channel, String recipientPhone, 
                      String messageContent, String status) {
        this.policy = policy;
        this.customer = policy.getClient();
        this.reminderType = reminderType;
        this.channel = channel;
        this.recipientPhone = recipientPhone;
        this.messageContent = messageContent;
        this.status = status;
        this.retryCount = 0;
    }

    /**
     * Check if this message can be retried
     */
    public boolean canRetry() {
        return "FAILED".equals(this.status) && this.retryCount < MAX_RETRY_COUNT;
    }

    /**
     * Check if max retries have been exhausted
     */
    public boolean isMaxRetriesExhausted() {
        return this.retryCount >= MAX_RETRY_COUNT;
    }
}