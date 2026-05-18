package com.renewai.controller;

import com.renewai.dto.MessageLogDto;
import com.renewai.entity.MessageLog;
import com.renewai.repository.MessageLogRepository;
import com.renewai.service.MessageService;
import com.renewai.service.RenewalSchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for message log operations
 * Handles viewing message history and retrying failed messages
 */
@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "*", maxAge = 3600)
public class MessageLogController {

    @Autowired
    private MessageLogRepository messageLogRepository;

    @Autowired
    private MessageService messageService;

    @Autowired
    private RenewalSchedulerService renewalSchedulerService;

    /**
     * GET /api/messages/logs
     * Retrieve all message logs sorted by most recent first
     */
    @GetMapping("/logs")
    public ResponseEntity<List<MessageLogDto>> getAllMessageLogs() {
        List<MessageLog> logs = messageLogRepository.findAllWithPolicyAndClient();

        List<MessageLogDto> dtos = logs.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * POST /api/messages/test-scheduler
     * Trigger the daily renewal scheduler manually for testing
     */
    @PostMapping("/test-scheduler")
    public ResponseEntity<?> triggerSchedulerManually() {
        try {
            renewalSchedulerService.triggerManualReminderCheck();
            Map<String, String> response = new HashMap<>();
            response.put("message", "Renewal scheduler triggered manually successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * POST /api/messages/send-bulk
     * Triggers the system to scan for expiring policies and send reminders
     */
    @PostMapping("/send-bulk")
    public ResponseEntity<?> sendBulkReminders() {
        try {
            renewalSchedulerService.triggerManualReminderCheck();
            Map<String, String> response = new HashMap<>();
            response.put("message", "Bulk reminders initiated successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * POST /api/messages/{id}/retry
     * 
     * Retry sending a failed message.
     * Business Rules:
     * - Only FAILED messages can be retried
     * - Max 3 retry attempts per message
     * - Idempotent: won't create duplicate SENT records
     * 
     * @param id the message log ID
     * @return updated message log DTO
     * 
     *         Response Examples:
     * 
     *         Success (200):
     *         {
     *         "id": 5,
     *         "status": "SENT",
     *         "retryCount": 2,
     *         "lastAttemptAt": "2026-04-21T10:30:00",
     *         "failureReason": null,
     *         "canRetry": false,
     *         "maxRetriesExhausted": false,
     *         ...
     *         }
     * 
     *         Error (400):
     *         { "error": "Max retry attempts (3) reached for message: 5" }
     * 
     *         Not Found (404):
     *         { "error": "Message log not found with id: 99" }
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryMessage(@PathVariable Long id) {
        try {
            MessageLog retried = messageService.retryMessage(id);
            return ResponseEntity.ok(mapToDto(retried));
        } catch (IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Map MessageLog entity to DTO, including retry mechanism fields
     */
    private MessageLogDto mapToDto(MessageLog log) {
        MessageLogDto dto = new MessageLogDto();
        dto.setId(log.getId());
        dto.setReminderType(log.getReminderType());
        dto.setRecipientPhone(log.getRecipientPhone());
        dto.setMessageContent(log.getMessageContent());
        dto.setStatus(log.getStatus());
        dto.setChannel(log.getChannel());
        dto.setSentAt(log.getSentAt());

        // Retry mechanism fields
        dto.setRetryCount(log.getRetryCount());
        dto.setLastAttemptAt(log.getLastAttemptAt());
        dto.setFailureReason(log.getFailureReason());
        dto.setCanRetry(log.canRetry());
        dto.setMaxRetriesExhausted(log.isMaxRetriesExhausted());

        if (log.getPolicy() != null) {
            dto.setPolicyId(log.getPolicy().getId());
            dto.setPolicyNumber(log.getPolicy().getPolicyNumber());
            if (log.getPolicy().getClient() != null) {
                dto.setCustomerId(log.getPolicy().getClient().getId());
                dto.setClientName(log.getPolicy().getClient().getFullName());
            }
        }
        return dto;
    }
}
