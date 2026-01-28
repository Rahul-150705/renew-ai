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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Service for generating and sending renewal reminder messages
 * This service contains the AI integration point for message generation
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
    public String generateRenewalMessage(Policy policy, int daysUntilExpiry) {
        // Template-based message generation (can be replaced with AI)
        String clientName = policy.getClient().getFullName();
        String policyType = policy.getPolicyType();
        String policyNumber = policy.getPolicyNumber();
        LocalDate expiryDate = policy.getExpiryDate();
        String formattedDate = expiryDate.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
        String premium = policy.getPremium().toString();
        
        String message;
        
        if (daysUntilExpiry == 7) {
            message = String.format(
                "Dear %s, your %s policy (%s) is expiring in 7 days on %s. " +
                "Premium: ₹%s. Please renew soon to avoid coverage lapse. " +
                "Contact your agent for assistance.",
                clientName, policyType, policyNumber, formattedDate, premium
            );
        } else if (daysUntilExpiry == 3) {
            message = String.format(
                "URGENT: Dear %s, your %s policy (%s) expires in 3 days on %s! " +
                "Premium: ₹%s. Renew immediately to maintain continuous coverage. " +
                "Call your agent today.",
                clientName, policyType, policyNumber, formattedDate, premium
            );
        } else {
            message = String.format(
                "Dear %s, your %s policy (%s) is expiring soon on %s. " +
                "Premium: ₹%s. Please renew to continue your coverage.",
                clientName, policyType, policyNumber, formattedDate, premium
            );
        }
        
        // TODO: AI Enhancement Example (commented out)
        // message = generateMessageWithClaude(policy, daysUntilExpiry);
        
        return message;
    }
    
    /**
     * Example method showing how Claude API can be integrated
     * This is a placeholder showing the integration pattern
     * 
     * @param policy the insurance policy
     * @param daysUntilExpiry days until expiry
     * @return AI-generated message
     */
    /*
    private String generateMessageWithClaude(Policy policy, int daysUntilExpiry) {
        try {
            // Prepare prompt for Claude
            String prompt = String.format(
                "Generate a professional, concise insurance renewal reminder SMS (max 160 chars) with these details:\n" +
                "- Client: %s\n" +
                "- Policy Type: %s\n" +
                "- Policy Number: %s\n" +
                "- Expiry Date: %s\n" +
                "- Days Until Expiry: %d\n" +
                "- Premium: ₹%s\n" +
                "Tone: Urgent if 3 days, Reminder if 7 days",
                policy.getClient().getFullName(),
                policy.getPolicyType(),
                policy.getPolicyNumber(),
                policy.getExpiryDate(),
                daysUntilExpiry,
                policy.getPremium()
            );
            
            // Call Claude API here
            // String aiMessage = claudeApiClient.generateMessage(prompt);
            // return aiMessage;
            
            // Fallback to template if API fails
            return generateRenewalMessage(policy, daysUntilExpiry);
            
        } catch (Exception e) {
            logger.error("Claude API error, using template: " + e.getMessage());
            return generateRenewalMessage(policy, daysUntilExpiry);
        }
    }
    */
    
    /**
     * Send renewal reminder message
     * Checks for duplicates before sending
     * @param policy the policy
     * @param reminderType SEVEN_DAYS or THREE_DAYS
     * @param daysUntilExpiry days until expiry
     * @return true if message sent successfully
     */
    @Transactional
    public boolean sendRenewalReminder(Policy policy, String reminderType, int daysUntilExpiry) {
        // Check if message already sent
        if (messageLogRepository.existsByPolicyIdAndReminderType(policy.getId(), reminderType)) {
            logger.info("Reminder already sent for policy {} - {}", policy.getPolicyNumber(), reminderType);
            return false;
        }
        
        String phoneNumber = policy.getClient().getPhoneNumber();
        String message = generateRenewalMessage(policy, daysUntilExpiry);
        
        boolean sent = false;
        String status = "PENDING";
        String errorMessage = null;
        
        try {
            if (twilioEnabled) {
                // Send via Twilio
                sent = sendViaTwilio(phoneNumber, message);
                status = sent ? "SENT" : "FAILED";
            } else {
                // Mock mode - simulate sending
                logger.info("MOCK SMS: To {} - {}", phoneNumber, message);
                sent = true;
                status = "SENT";
            }
        } catch (Exception e) {
            logger.error("Error sending message: " + e.getMessage());
            status = "FAILED";
            errorMessage = e.getMessage();
        }
        
        // Log the message
        MessageLog messageLog = new MessageLog(policy, reminderType, phoneNumber, message, status);
        if (errorMessage != null) {
            messageLog.setErrorMessage(errorMessage);
        }
        messageLogRepository.save(messageLog);
        
        return sent;
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