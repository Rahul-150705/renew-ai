package com.renewai.service;

import com.renewai.entity.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Renewal Scheduler Service
 * 
 * WHY USE SCHEDULER?
 * - Automates the renewal reminder process without manual intervention
 * - Runs at a fixed time daily (9:00 AM) to check for expiring policies
 * - Ensures timely reminders are sent to clients
 * - Reduces agent workload by automating repetitive tasks
 * - Provides consistent, reliable reminder delivery
 * 
 * This service runs a scheduled job daily to:
 * 1. Find policies expiring in 7 days
 * 2. Find policies expiring in 3 days
 * 3. Send renewal reminders via MessageService
 * 4. Prevent duplicate messages using MessageLog
 */
@Service
public class RenewalSchedulerService {
    
    private static final Logger logger = LoggerFactory.getLogger(RenewalSchedulerService.class);
    
    @Autowired
    private PolicyService policyService;
    
    @Autowired
    private MessageService messageService;
    
    /**
     * Scheduled job to check for expiring policies and send reminders
     * 
     * Cron Expression: 0 0 9 * * ?
     * - Second: 0
     * - Minute: 0
     * - Hour: 9 (9:00 AM)
     * - Day of Month: * (every day)
     * - Month: * (every month)
     * - Day of Week: ? (any day)
     * 
     * This runs every day at 9:00 AM
     */
    @Scheduled(cron = "${renewal.scheduler.cron}")
    public void checkExpiringPoliciesAndSendReminders() {
        logger.info("=== Starting daily renewal reminder job ===");
        
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysLater = today.plusDays(7);
        LocalDate threeDaysLater = today.plusDays(3);
        
        try {
            // Process 7-day reminders
            processReminders(sevenDaysLater, "SEVEN_DAYS", 7);
            
            // Process 3-day reminders
            processReminders(threeDaysLater, "THREE_DAYS", 3);
            
            logger.info("=== Daily renewal reminder job completed successfully ===");
            
        } catch (Exception e) {
            // FAILURE HANDLING:
            // Log error but don't crash the scheduler
            // Next execution will retry failed messages
            logger.error("Error in renewal reminder job: " + e.getMessage(), e);
        }
    }
    
    /**
     * Process reminders for a specific expiry date
     * @param expiryDate the expiry date to check
     * @param reminderType SEVEN_DAYS or THREE_DAYS
     * @param daysUntilExpiry days until expiry
     */
    private void processReminders(LocalDate expiryDate, String reminderType, int daysUntilExpiry) {
        logger.info("Processing {} reminders for date: {}", reminderType, expiryDate);
        
        // Find policies expiring on this date
        List<Policy> expiringPolicies = policyService.getPoliciesExpiringOn(expiryDate);
        
        if (expiringPolicies.isEmpty()) {
            logger.info("No policies expiring on {}", expiryDate);
            return;
        }
        
        logger.info("Found {} policies expiring on {}", expiringPolicies.size(), expiryDate);
        
        int sentCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        
        // Process each policy
        for (Policy policy : expiringPolicies) {
            try {
                boolean sent = messageService.sendRenewalReminder(policy, reminderType, daysUntilExpiry);
                
                if (sent) {
                    sentCount++;
                    logger.info("Reminder sent for policy: {} to {}", 
                        policy.getPolicyNumber(), 
                        policy.getClient().getPhoneNumber());
                } else {
                    skippedCount++;
                    logger.debug("Reminder skipped (already sent) for policy: {}", 
                        policy.getPolicyNumber());
                }
                
            } catch (Exception e) {
                failedCount++;
                logger.error("Failed to send reminder for policy {}: {}", 
                    policy.getPolicyNumber(), 
                    e.getMessage());
            }
        }
        
        logger.info("{} Summary - Total: {}, Sent: {}, Skipped: {}, Failed: {}", 
            reminderType, 
            expiringPolicies.size(), 
            sentCount, 
            skippedCount, 
            failedCount);
    }
    
    /**
     * Manual trigger for testing (optional)
     * Can be called via API endpoint for testing purposes
     */
    public void triggerManualReminderCheck() {
        logger.info("=== Manual trigger initiated ===");
        checkExpiringPoliciesAndSendReminders();
    }
}