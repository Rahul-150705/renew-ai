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
 * Provides database operations for message tracking, duplicate prevention, and retry logic
 */
@Repository
public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {
    
    /**
     * Eagerly fetch message logs with their associated policies and clients
     * to prevent N+1 select query issues.
     */
    @Query("SELECT ml FROM MessageLog ml " +
           "LEFT JOIN FETCH ml.policy p " +
           "LEFT JOIN FETCH p.client c " +
           "LEFT JOIN FETCH ml.customer cust " +
           "ORDER BY ml.sentAt DESC")
    List<MessageLog> findAllWithPolicyAndClient();

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
     * Find all FAILED messages that still have retries remaining
     * Used by the scheduler to auto-retry failed messages
     */
    @Query("SELECT ml FROM MessageLog ml WHERE ml.status = 'FAILED' AND ml.retryCount < :maxRetries")
    List<MessageLog> findRetryableMessages(@Param("maxRetries") int maxRetries);
    
    /**
     * Check if message already exists for policy, reminder type, and channel
     * Used before sending to prevent duplicates per channel
     */
    @Query("SELECT CASE WHEN COUNT(ml) > 0 THEN true ELSE false END " +
           "FROM MessageLog ml WHERE ml.policy.id = :policyId AND ml.reminderType = :reminderType AND ml.channel = :channel")
    boolean existsByPolicyIdAndReminderTypeAndChannel(
        @Param("policyId") Long policyId, 
        @Param("reminderType") String reminderType,
        @Param("channel") String channel
    );

    /**
     * Check if a SENT message already exists for policy, reminder type, and channel
     * Used for idempotency: prevents retries from creating duplicate SENT records
     */
    @Query("SELECT CASE WHEN COUNT(ml) > 0 THEN true ELSE false END " +
           "FROM MessageLog ml WHERE ml.policy.id = :policyId AND ml.reminderType = :reminderType " +
           "AND ml.channel = :channel AND ml.status = 'SENT'")
    boolean existsSentMessageForPolicyAndChannel(
        @Param("policyId") Long policyId,
        @Param("reminderType") String reminderType,
        @Param("channel") String channel
    );

    @Query("SELECT COUNT(ml) FROM MessageLog ml WHERE ml.policy.client.agent.id = :agentId AND ml.status = 'FAILED'")
    long countFailedByAgentId(@Param("agentId") Long agentId);

    @Query("SELECT COUNT(ml) FROM MessageLog ml WHERE ml.policy.client.agent.id = :agentId AND CAST(ml.sentAt AS date) = CURRENT_DATE")
    long countSentTodayByAgentId(@Param("agentId") Long agentId);

    @Query("SELECT COUNT(ml) FROM MessageLog ml WHERE ml.policy.client.agent.id = :agentId AND ml.channel = :channel AND ml.status = 'SENT'")
    long countSuccessByChannelAndAgentId(@Param("channel") String channel, @Param("agentId") Long agentId);

    @Query("SELECT COUNT(ml) FROM MessageLog ml WHERE ml.policy.client.agent.id = :agentId AND ml.channel = :channel")
    long countTotalByChannelAndAgentId(@Param("channel") String channel, @Param("agentId") Long agentId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE MessageLog ml SET ml.status = 'RESOLVED' WHERE ml.policy.id = :policyId AND ml.status = 'FAILED'")
    void resolveFailedMessagesByPolicy(@Param("policyId") Long policyId);

    @Query("SELECT COUNT(ml) FROM MessageLog ml WHERE ml.policy.client.agent.id = :agentId AND ml.retryCount >= 3 AND ml.status = 'FAILED'")
    long countExhaustedRetries(@Param("agentId") Long agentId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM MessageLog ml WHERE ml.policy.id = :policyId")
    void deleteByPolicyId(@Param("policyId") Long policyId);
}