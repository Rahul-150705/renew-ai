package com.renewai.repository;

import com.renewai.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Agent entity
 * Provides database operations for agent authentication and management
 */
@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {
    
    /**
     * Find agent by username for authentication
     * @param username the agent's username
     * @return Optional containing agent if found
     */
    Optional<Agent> findByUsername(String username);
    
    /**
     * Find agent by email
     * @param email the agent's email
     * @return Optional containing agent if found
     */
    Optional<Agent> findByEmail(String email);
    
    /**
     * Check if username already exists
     * @param username the username to check
     * @return true if username exists
     */
    boolean existsByUsername(String username);
    
    /**
     * Check if email already exists
     * @param email the email to check
     * @return true if email exists
     */
    boolean existsByEmail(String email);
}