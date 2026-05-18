package com.renewai.service;

import com.renewai.entity.MessageLog;
import com.renewai.entity.Policy;
import com.renewai.repository.MessageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

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
    
    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;
    
    @Value("${twilio.whatsapp.number}")
    private String twilioWhatsappNumber;
    
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
    @CacheEvict(value = {"dashboardSummary", "aiInsights", "conversionFunnel"}, allEntries = true)
    public boolean sendRenewalReminder(Policy policy, String reminderType, int daysUntilExpiry) {
        String whatsappNumber = policy.getClient().getWhatsappNumber();
        String phoneNumber = policy.getClient().getPhoneNumber();
        
        boolean hasWhatsapp = whatsappNumber != null && !whatsappNumber.trim().isEmpty();
        boolean hasSms = phoneNumber != null && !phoneNumber.trim().isEmpty();
        
        if (hasWhatsapp) {
            // If they have WhatsApp, ONLY send via WhatsApp
            return sendGenericReminder(policy, reminderType, daysUntilExpiry, "WHATSAPP");
        } else if (hasSms) {
            // If they only have SMS, send via SMS
            return sendGenericReminder(policy, reminderType, daysUntilExpiry, "SMS");
        } else {
            logger.warn("Cannot send reminder: Client {} has no phone or WhatsApp number.", policy.getClient().getFullName());
            return false;
        }
    }
    
    @Transactional
    @CacheEvict(value = {"dashboardSummary", "aiInsights", "conversionFunnel"}, allEntries = true)
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
                sent = sendViaTwilio(phoneNumber, message, channel);
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
    @CacheEvict(value = {"dashboardSummary", "aiInsights", "conversionFunnel"}, allEntries = true)
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
                sent = sendViaTwilio(phoneNumber, message, messageLog.getChannel());
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
     * @param channel SMS or WHATSAPP
     * @return true if sent successfully
     */
    private boolean sendViaTwilio(String phoneNumber, String message, String channel) {
        try {
            String fromNumber;
            String toNumber;
            
            if ("WHATSAPP".equalsIgnoreCase(channel)) {
                // Twilio WhatsApp numbers must be prefixed with "whatsapp:"
                fromNumber = "whatsapp:" + twilioWhatsappNumber;
                // Only add prefix if it doesn't already have it
                toNumber = phoneNumber.startsWith("whatsapp:") ? phoneNumber : "whatsapp:" + phoneNumber;
            } else {
                fromNumber = twilioPhoneNumber;
                toNumber = phoneNumber;
            }
            
            // Ensure numbers have '+' prefix if needed, though usually Twilio handles E.164 natively
            // But let's assume properties and DB have correctly formatted numbers like +1234567890
            
            Message twilioMessage = Message.creator(
                new PhoneNumber(toNumber),
                new PhoneNumber(fromNumber),
                message
            ).create();
            
            logger.info("Twilio message created with SID: {}", twilioMessage.getSid());
            
            Message.Status status = twilioMessage.getStatus();
            // In Twilio, created messages typically have status QUEUED, SENT, or DELIVERED.
            // FAILED or UNDELIVERED indicate immediate failure.
            return status != Message.Status.FAILED && status != Message.Status.UNDELIVERED;
            
        } catch (Exception e) {
            logger.error("Failed to send message via Twilio: {}", e.getMessage(), e);
            return false;
        }
    }
}