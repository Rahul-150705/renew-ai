package com.renewai.service;

import com.renewai.entity.MessageLog;
import com.renewai.entity.Policy;
import com.renewai.repository.MessageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for generating and sending renewal reminder messages
 * Supports retry mechanism with idempotency guarantees
 */
@Service
public class MessageService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    
    @Autowired
    private MessageLogRepository messageLogRepository;
    
    @Value("${twilio.enabled:false}")
    private boolean twilioEnabled;
    
    /**
     * Generate a renewal reminder message
     * 
     * AI INTEGRATION POINT:
     * This method can be enhanced to use Claude API for personalized message generation
     * Example: Call Claude API with policy details to generate context-aware messages
     * 
     * Implementation approach:
     * 1. Prepare policy data (type, expiry date, premium, client name)
     * 2. Send to Claude API with prompt: "Generate a professional renewal reminder for..."
     * 3. Parse Claude's response and use it as the message
     * 4. Keep fallback template if API fails
     * 
     * @param policy the insurance policy
     * @param daysUntilExpiry days until policy expires
     * @return generated message content
     */
    public String generateRenewalMessage(Policy policy, int daysUntilExpiry, boolean isWhatsapp) {
        String clientName = policy.getClient().getFullName();
        String vehicleType = policy.getVehicleType() != null ? policy.getVehicleType() : policy.getPolicyType();
        String regNumber = policy.getRegistrationNumber() != null ? policy.getRegistrationNumber() : "N/A";
        String policyNumber = policy.getPolicyNumber();
        String expiryDateStr = policy.getExpiryDate().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
        
        if (daysUntilExpiry == 7) {
            return String.format(
                "Dear %s, your %s insurance (Reg: %s, Policy: %s) " +
                "expires on %s. Please renew within 7 days to avoid a coverage lapse. " +
                "Contact your agent for help.",
                clientName, vehicleType, regNumber, policyNumber, expiryDateStr
            );
        } else if (daysUntilExpiry == 3) {
            return String.format(
                "URGENT: Dear %s, your %s insurance (Reg: %s) expires in " +
                "3 days on %s. Renew immediately to stay covered. Call your agent today.",
                clientName, vehicleType, regNumber, expiryDateStr
            );
        } else {
            // Expiry day message
            return String.format(
                "FINAL NOTICE: Dear %s, your %s insurance (Reg: %s, " +
                "Policy: %s) expires TODAY %s. Renew now to avoid driving " +
                "uninsured. Contact your agent immediately.",
                clientName, vehicleType, regNumber, policyNumber, expiryDateStr
            );
        }
    }
    
    @Transactional
    public boolean sendRenewalReminder(Policy policy, String reminderType, int daysUntilExpiry) {
        boolean smsSent = sendGenericReminder(policy, reminderType, daysUntilExpiry, "SMS");
        boolean whatsappSent = false;
        
        if (policy.getClient().getWhatsappNumber() != null && !policy.getClient().getWhatsappNumber().isEmpty()) {
            whatsappSent = sendGenericReminder(policy, reminderType, daysUntilExpiry, "WHATSAPP");
        }
        
        return smsSent || whatsappSent;
    }
    
    @Transactional
    public boolean sendGenericReminder(Policy policy, String reminderType, int daysUntilExpiry, String channel) {
        if (messageLogRepository.existsByPolicyIdAndReminderTypeAndChannel(policy.getId(), reminderType, channel)) {
            logger.info("Reminder already sent for policy {} - {} - {}", policy.getPolicyNumber(), reminderType, channel);
            return false;
        }
        
        String phoneNumber = "WHATSAPP".equals(channel) ? policy.getClient().getWhatsappNumber() : policy.getClient().getPhoneNumber();
        String message = generateRenewalMessage(policy, daysUntilExpiry, "WHATSAPP".equals(channel));
        
        boolean sent = false;
        String status = "PENDING";
        String failureReason = null;
        
        try {
            if (twilioEnabled) {
                sent = sendViaTwilio(phoneNumber, message);
                status = sent ? "SENT" : "FAILED";
                if (!sent) {
                    failureReason = "Twilio returned failure status";
                }
            } else {
                logger.info("MOCK {}: To {} - {}", channel, phoneNumber, message);
                sent = true;
                status = "SENT";
            }
        } catch (Exception e) {
            logger.error("Error sending message: " + e.getMessage());
            status = "FAILED";
            failureReason = e.getMessage();
        }
        
        MessageLog messageLog = new MessageLog(policy, reminderType, channel, phoneNumber, message, status);
        messageLog.setLastAttemptAt(LocalDateTime.now());
        if (failureReason != null) {
            messageLog.setFailureReason(failureReason);
            messageLog.setErrorMessage(failureReason);
        }
        messageLogRepository.save(messageLog);
        
        return sent;
    }

    /**
     * Retry sending a failed message
     * 
     * Business Rules:
     * - Only FAILED messages can be retried
     * - Max 3 retry attempts
     * - Each attempt updates retry_count, last_attempt_at, failure_reason
     * - Idempotency: if a SENT record already exists for same policy/reminder/channel, skip
     * 
     * @param messageLogId the ID of the failed message log
     * @return the updated message log
     */
    @Transactional
    public MessageLog retryMessage(Long messageLogId) {
        MessageLog messageLog = messageLogRepository.findById(messageLogId)
                .orElseThrow(() -> new RuntimeException("Message log not found with id: " + messageLogId));

        // Validate state
        if (!"FAILED".equals(messageLog.getStatus())) {
            throw new IllegalStateException("Only FAILED messages can be retried. Current status: " + messageLog.getStatus());
        }

        if (messageLog.getRetryCount() >= MessageLog.MAX_RETRY_COUNT) {
            throw new IllegalStateException("Max retry attempts (" + MessageLog.MAX_RETRY_COUNT + ") reached for message: " + messageLogId);
        }

        // Idempotency check: prevent duplicate SENT messages
        if (messageLogRepository.existsSentMessageForPolicyAndChannel(
                messageLog.getPolicy().getId(), 
                messageLog.getReminderType(), 
                messageLog.getChannel())) {
            throw new IllegalStateException("A SENT message already exists for this policy/reminder/channel combination");
        }

        // Increment retry count and update timestamp
        messageLog.setRetryCount(messageLog.getRetryCount() + 1);
        messageLog.setLastAttemptAt(LocalDateTime.now());

        // Attempt to resend
        String phoneNumber = messageLog.getRecipientPhone();
        String message = messageLog.getMessageContent();

        try {
            boolean sent;
            if (twilioEnabled) {
                sent = sendViaTwilio(phoneNumber, message);
            } else {
                logger.info("MOCK RETRY {}: To {} - {} (attempt {})", 
                    messageLog.getChannel(), phoneNumber, message, messageLog.getRetryCount());
                sent = true;
            }

            if (sent) {
                messageLog.setStatus("SENT");
                messageLog.setFailureReason(null);
                messageLog.setErrorMessage(null);
                logger.info("Retry successful for message {} (attempt {})", messageLogId, messageLog.getRetryCount());
            } else {
                messageLog.setStatus("FAILED");
                messageLog.setFailureReason("Twilio returned failure status on retry attempt " + messageLog.getRetryCount());
                messageLog.setErrorMessage(messageLog.getFailureReason());
            }
        } catch (Exception e) {
            messageLog.setStatus("FAILED");
            messageLog.setFailureReason("Retry attempt " + messageLog.getRetryCount() + " failed: " + e.getMessage());
            messageLog.setErrorMessage(messageLog.getFailureReason());
            logger.error("Retry failed for message {}: {}", messageLogId, e.getMessage());
        }

        return messageLogRepository.save(messageLog);
    }
    
    /**
     * Send message via Twilio
     * @param phoneNumber recipient phone number
     * @param message message content
     * @return true if sent successfully
     */
    private boolean sendViaTwilio(String phoneNumber, String message) {
        // TODO: Implement Twilio integration
        // Example:
        // Message twilioMessage = Message.creator(
        //     new PhoneNumber(phoneNumber),
        //     new PhoneNumber(twilioPhoneNumber),
        //     message
        // ).create();
        // return twilioMessage.getStatus() == Message.Status.SENT;
        
        logger.info("Twilio integration pending - message queued");
        return true;
    }
}