package com.renewai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * MessageLog Entity - Tracks all renewal reminder messages sent
 * Prevents duplicate messages using composite unique constraint
 */
@Entity
@Table(
    name = "message_logs",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"policy_id", "reminder_type"},
        name = "uk_policy_reminder"
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // Reference to the policy for which message was sent
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;
    
    // Type of reminder: SEVEN_DAYS or THREE_DAYS
    @Column(nullable = false, length = 20)
    private String reminderType;
    
    // Phone number where message was sent
    @Column(nullable = false, length = 15)
    private String recipientPhone;
    
    // The actual message content sent
    @Column(nullable = false, length = 500)
    private String messageContent;
    
    // Message delivery status: SENT, FAILED, PENDING
    @Column(nullable = false, length = 20)
    private String status = "SENT";
    
    // External message ID from Twilio (if applicable)
    @Column(length = 100)
    private String externalMessageId;
    
    // Error message if sending failed
    @Column(length = 500)
    private String errorMessage;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime sentAt;
    
    /**
     * Constructor for easy creation during message sending
     */
    public MessageLog(Policy policy, String reminderType, String recipientPhone, 
                      String messageContent, String status) {
        this.policy = policy;
        this.reminderType = reminderType;
        this.recipientPhone = recipientPhone;
        this.messageContent = messageContent;
        this.status = status;
    }
}