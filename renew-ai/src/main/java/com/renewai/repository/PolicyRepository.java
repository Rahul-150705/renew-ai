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
}