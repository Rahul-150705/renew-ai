package com.renewai.repository;

import com.renewai.entity.Client;
import com.renewai.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Policy entity
 * Provides database operations for policy management and renewal tracking
 */
@Repository
public interface PolicyRepository extends JpaRepository<Policy, Long> {
    
    /**
     * Find all policies for a specific client
     * @param client the client
     * @return list of policies
     */
    List<Policy> findByClient(Client client);
    
    /**
     * Find policy by policy number
     * @param policyNumber the policy number
     * @return Optional containing policy if found
     */
    Optional<Policy> findByPolicyNumber(String policyNumber);
    
    /**
     * Find all active policies expiring on a specific date
     * Critical query for renewal scheduler
     * @param expiryDate the expiry date to check
     * @return list of policies expiring on that date
     */
    @Query("SELECT p FROM Policy p WHERE p.expiryDate = :expiryDate AND p.status = 'ACTIVE'")
    List<Policy> findPoliciesExpiringOn(@Param("expiryDate") LocalDate expiryDate);
    
    /**
     * Find all active policies expiring between two dates
     * Used for batch processing in scheduler
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @return list of policies expiring in date range
     */
    @Query("SELECT p FROM Policy p WHERE p.expiryDate BETWEEN :startDate AND :endDate AND p.status = 'ACTIVE'")
    List<Policy> findPoliciesExpiringBetween(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate
    );
    
    /**
     * Check if policy number already exists
     * @param policyNumber the policy number to check
     * @return true if policy number exists
     */
    boolean existsByPolicyNumber(String policyNumber);

    /**
     * Find all active policies expiring before a date
     * Used to mark them as EXPIRED
     */
    @Query("SELECT p FROM Policy p WHERE p.expiryDate < :date AND p.status = 'ACTIVE'")
    List<Policy> findActivePoliciesExpiringBefore(@Param("date") LocalDate date);

    @Query("SELECT p FROM Policy p WHERE p.client.agent.id = :agentId")
    List<Policy> findByAgentId(@Param("agentId") Long agentId);

    @Query("SELECT COUNT(p) FROM Policy p WHERE p.client.agent.id = :agentId")
    long countByAgentId(@Param("agentId") Long agentId);

    @Query(value = "SELECT COUNT(p) FROM policies p " +
           "JOIN clients c ON p.client_id = c.id " +
           "WHERE c.agent_id = :agentId " +
           "AND p.status = 'ACTIVE' " +
           "AND p.expiry_date BETWEEN :startDate AND :endDate", nativeQuery = true)
    long countExpiringSoonByAgentId(@Param("agentId") Long agentId, @Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    @Query("SELECT new com.renewai.dto.ChartDataDto(p.vehicleType, COUNT(p)) FROM Policy p WHERE p.client.agent.id = :agentId GROUP BY p.vehicleType")
    List<com.renewai.dto.ChartDataDto> findPolicyDistributionByAgentId(@Param("agentId") Long agentId);

    @Query("SELECT COUNT(p) FROM Policy p WHERE p.client.agent.id = :agentId AND (p.status = 'RENEWED' OR p.renewalStatus = 'AUTO_RENEWED' OR p.renewalStatus = 'MANUAL_RENEWED')")
    long countRenewedByAgentId(@Param("agentId") Long agentId);

    @Query(value = "SELECT COUNT(p) FROM policies p " +
           "JOIN clients c ON p.client_id = c.id " +
           "WHERE c.agent_id = :agentId " +
           "AND (p.status = 'RENEWED' OR p.renewal_status = 'AUTO_RENEWED' OR p.renewal_status = 'MANUAL_RENEWED') " +
           "AND p.expiry_date BETWEEN :startDate AND :endDate", nativeQuery = true)
    long countRenewedByAgentBetween(@Param("agentId") Long agentId, @Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    @Query("SELECT COUNT(p) FROM Policy p WHERE p.client.agent.id = :agentId AND p.createdAt BETWEEN :startDate AND :endDate")
    long countCreatedByAgentBetween(@Param("agentId") Long agentId, @Param("startDate") java.time.LocalDateTime startDate, @Param("endDate") java.time.LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(p.premium), 0) FROM Policy p WHERE p.client.agent.id = :agentId AND p.status = 'ACTIVE'")
    java.math.BigDecimal sumPremiumByAgentId(@Param("agentId") Long agentId);

    @Query(value = "SELECT COALESCE(SUM(p.premium), 0) FROM policies p " +
           "JOIN clients c ON p.client_id = c.id " +
           "WHERE c.agent_id = :agentId " +
           "AND p.status = 'ACTIVE' " +
           "AND p.expiry_date BETWEEN :startDate AND :endDate", nativeQuery = true)
    java.math.BigDecimal sumPremiumForExpiringPoliciesInRange(@Param("agentId") Long agentId, @Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    @Query(value = "SELECT COALESCE(SUM(p.premium), 0) FROM policies p " +
           "JOIN clients c ON p.client_id = c.id " +
           "WHERE c.agent_id = :agentId " +
           "AND p.status = 'ACTIVE' " +
           "AND EXTRACT(MONTH FROM p.created_at) = EXTRACT(MONTH FROM CURRENT_DATE) " +
           "AND EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)", nativeQuery = true)
    java.math.BigDecimal sumActivePremiumThisMonth(@Param("agentId") Long agentId);

    @Query(value = "SELECT COALESCE(SUM(p.premium), 0) FROM policies p " +
           "JOIN clients c ON p.client_id = c.id " +
           "WHERE c.agent_id = :agentId " +
           "AND p.status = 'ACTIVE' " +
           "AND p.created_at >= DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 month') " +
           "AND p.created_at < DATE_TRUNC('month', CURRENT_DATE)", nativeQuery = true)
    java.math.BigDecimal sumActivePremiumLastMonth(@Param("agentId") Long agentId);

    @Query("SELECT p.expiryDate, COUNT(p) FROM Policy p WHERE p.client.agent.id = :agentId AND p.expiryDate BETWEEN :startDate AND :endDate GROUP BY p.expiryDate ORDER BY p.expiryDate")
    List<Object[]> findProjectedRenewalsRaw(@Param("agentId") Long agentId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query(value = "SELECT CAST(EXTRACT(MONTH FROM p.created_at) AS INTEGER) as month, COALESCE(SUM(p.premium), 0) as revenue " +
           "FROM policies p " +
           "JOIN clients c ON p.client_id = c.id " +
           "WHERE c.agent_id = :agentId " +
           "AND p.created_at >= :startDate " +
           "GROUP BY month " +
           "ORDER BY month", nativeQuery = true)
    List<Object[]> findMonthlyRevenueRaw(@Param("agentId") Long agentId, @Param("startDate") java.time.LocalDateTime startDate);

    @Query("SELECT COUNT(p) FROM Policy p WHERE p.client.agent.id = :agentId AND p.status = 'EXPIRED' AND p.renewalStatus = 'PENDING'")
    long countLostPoliciesByAgentId(@Param("agentId") Long agentId);

    @Query(value = "SELECT COUNT(p) FROM policies p " +
           "JOIN clients c ON p.client_id = c.id " +
           "WHERE c.agent_id = :agentId " +
           "AND p.renewal_status = 'RENEWED' " +
           "AND EXTRACT(MONTH FROM p.created_at) = EXTRACT(MONTH FROM CURRENT_DATE) " +
           "AND EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)", nativeQuery = true)
    long countRenewalsThisMonth(@Param("agentId") Long agentId);

    @Query(value = "SELECT COALESCE(SUM(p.premium), 0) FROM policies p " +
           "JOIN clients c ON p.client_id = c.id " +
           "WHERE c.agent_id = :agentId " +
           "AND p.renewal_status = 'RENEWED' " +
           "AND p.created_at BETWEEN :startDate AND :endDate", nativeQuery = true)
    java.math.BigDecimal sumRenewedPremiumBetween(
        @Param("agentId") Long agentId, 
        @Param("startDate") java.time.LocalDateTime startDate, 
        @Param("endDate") java.time.LocalDateTime endDate
    );

    @Query(value = "SELECT COUNT(p) FROM policies p " +
           "JOIN clients c ON p.client_id = c.id " +
           "WHERE c.agent_id = :agentId " +
           "AND p.status = 'EXPIRED' " +
           "AND p.renewal_status = 'PENDING' " +
           "AND p.expiry_date BETWEEN :startDate AND :endDate", nativeQuery = true)
    long countLostPoliciesBetween(
        @Param("agentId") Long agentId,
        @Param("startDate") java.time.LocalDate startDate,
        @Param("endDate") java.time.LocalDate endDate
    );
}