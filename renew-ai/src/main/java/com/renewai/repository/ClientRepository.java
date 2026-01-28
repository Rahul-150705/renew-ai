package com.renewai.repository;

import com.renewai.entity.Agent;
import com.renewai.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Client entity
 * Provides database operations for client management
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    
    /**
     * Find all clients managed by a specific agent
     * @param agent the agent
     * @return list of clients
     */
    List<Client> findByAgent(Agent agent);
    
    /**
     * Find client by email
     * @param email the client's email
     * @return Optional containing client if found
     */
    Optional<Client> findByEmail(String email);
    
    /**
     * Find client by phone number
     * @param phoneNumber the client's phone number
     * @return Optional containing client if found
     */
    Optional<Client> findByPhoneNumber(String phoneNumber);
    
    /**
     * Check if email already exists
     * @param email the email to check
     * @return true if email exists
     */
    boolean existsByEmail(String email);
}