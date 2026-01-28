package com.renewai.repository;

import com.renewai.entity.MessageLog;
import com.renewai.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MessageLog entity
 * Provides database operations for message tracking and duplicate prevention
 */
@Repository
public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {
    
    /**
     * Find all messages sent for a specific policy
     * @param policy the policy
     * @return list of message logs
     */
    List<MessageLog> findByPolicy(Policy policy);
    
    /**
     * Check if a message has already been sent for a policy and reminder type
     * Critical for preventing duplicate messages
     * @param policy the policy
     * @param reminderType the reminder type (SEVEN_DAYS or THREE_DAYS)
     * @return Optional containing message log if already sent
     */
    @Query("SELECT ml FROM MessageLog ml WHERE ml.policy = :policy AND ml.reminderType = :reminderType")
    Optional<MessageLog> findByPolicyAndReminderType(
        @Param("policy") Policy policy, 
        @Param("reminderType") String reminderType
    );
    
    /**
     * Find all messages with a specific status
     * Useful for retry logic on failed messages
     * @param status the message status
     * @return list of message logs
     */
    List<MessageLog> findByStatus(String status);
    
    /**
     * Check if message already exists for policy and reminder type
     * Used before sending to prevent duplicates
     * @param policyId the policy ID
     * @param reminderType the reminder type
     * @return true if message already sent
     */
    @Query("SELECT CASE WHEN COUNT(ml) > 0 THEN true ELSE false END " +
           "FROM MessageLog ml WHERE ml.policy.id = :policyId AND ml.reminderType = :reminderType")
    boolean existsByPolicyIdAndReminderType(
        @Param("policyId") Long policyId, 
        @Param("reminderType") String reminderType
    );
}