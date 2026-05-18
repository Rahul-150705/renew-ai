package com.renewai.service;

import com.renewai.entity.MessageLog;
import com.renewai.entity.Policy;
import com.renewai.repository.MessageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class RenewalSchedulerService {
    
    private static final Logger logger = LoggerFactory.getLogger(RenewalSchedulerService.class);
    
    @Autowired
    private PolicyService policyService;
    
    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageLogRepository messageLogRepository;
    
    /**
     * Scheduled job to mark policies as EXPIRED
     */
    @Scheduled(cron = "0 0 8 * * ?")
    @CacheEvict(value = {"dashboardSummary", "renewalTrends", "revenueTrends", "policyDistribution", "aiInsights", "conversionFunnel"}, allEntries = true)
    public void updateExpiredPolicies() {
        logger.info("=== Starting daily policy expiration job ===");
        try {
            LocalDate today = LocalDate.now();
            List<Policy> expiredPolicies = policyService.getAllPoliciesExpiringBefore(today);
            for (Policy policy : expiredPolicies) {
                if ("ACTIVE".equals(policy.getStatus())) {
                    policy.setStatus("EXPIRED");
                    policyService.savePolicy(policy);
                }
            }
            logger.info("Marked {} policies as EXPIRED", expiredPolicies.size());
        } catch (Exception e) {
            logger.error("Error in policy expiration job: " + e.getMessage(), e);
        }
    }

    /**
     * Scheduled job to check for expiring policies and send reminders
     */
    @Scheduled(cron = "${renewal.scheduler.cron}")
    @CacheEvict(value = {"dashboardSummary", "renewalTrends", "revenueTrends", "policyDistribution", "aiInsights", "conversionFunnel"}, allEntries = true)
    public void checkExpiringPoliciesAndSendReminders() {
        logger.info("=== Starting daily renewal reminder job ===");
        
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysLater = today.plusDays(7);
        LocalDate threeDaysLater = today.plusDays(3);
        
        try {
            processReminders(sevenDaysLater, "SEVEN_DAYS", 7);
            processReminders(threeDaysLater, "THREE_DAYS", 3);
            processReminders(today, "EXPIRY_DAY", 0);
            
            logger.info("=== Daily renewal reminder job completed successfully ===");
            
            // Trigger auto-retry immediately after daily job
            autoRetryFailedMessages();
            
        } catch (Exception e) {
            logger.error("Error in renewal reminder job: " + e.getMessage(), e);
        }
    }

    /**
     * AUTO-RETRY TASK
     * Runs every hour to automatically retry failed WhatsApp/SMS messages.
     * Cap: 3 attempts total (MessageLog.MAX_RETRY_COUNT)
     */
    @Scheduled(fixedDelay = 3600000) // 1 hour
    public void autoRetryFailedMessages() {
        logger.info("=== Starting automatic message retry job ===");
        
        List<MessageLog> retryable = messageLogRepository.findRetryableMessages(MessageLog.MAX_RETRY_COUNT);
        
        if (retryable.isEmpty()) {
            logger.info("No failed messages found for retry.");
            return;
        }
        
        logger.info("Found {} failed messages eligible for retry.", retryable.size());
        
        int successCount = 0;
        for (MessageLog log : retryable) {
            try {
                MessageLog result = messageService.retryMessage(log.getId());
                if ("SENT".equals(result.getStatus())) {
                    successCount++;
                }
            } catch (Exception e) {
                logger.error("Auto-retry failed for message log ID {}: {}", log.getId(), e.getMessage());
            }
        }
        
        logger.info("=== Auto-retry job finished. Success: {}/{} ===", successCount, retryable.size());
    }
    
    private void processReminders(LocalDate expiryDate, String reminderType, int daysUntilExpiry) {
        logger.info("Processing {} reminders for date: {}", reminderType, expiryDate);
        List<Policy> expiringPolicies = policyService.getPoliciesExpiringOn(expiryDate);
        
        if (expiringPolicies.isEmpty()) {
            return;
        }
        
        for (Policy policy : expiringPolicies) {
            try {
                messageService.sendRenewalReminder(policy, reminderType, daysUntilExpiry);
            } catch (Exception e) {
                logger.error("Failed to send reminder for policy {}: {}", policy.getPolicyNumber(), e.getMessage());
            }
        }
    }
    
    public void triggerManualReminderCheck() {
        logger.info("=== Manual trigger initiated ===");
        checkExpiringPoliciesAndSendReminders();
    }
}