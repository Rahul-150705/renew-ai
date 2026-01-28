package com.renewai.service;

import com.renewai.dto.PolicyRequest;
import com.renewai.entity.Client;
import com.renewai.entity.Policy;
import com.renewai.repository.ClientRepository;
import com.renewai.repository.PolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for insurance policy management
 * Handles creating and retrieving policies
 */
@Service
public class PolicyService {
    
    @Autowired
    private PolicyRepository policyRepository;
    
    @Autowired
    private ClientRepository clientRepository;
    
    /**
     * Create a new insurance policy for a client
     * @param policyRequest policy details
     * @return created policy
     */
    @Transactional
    public Policy createPolicy(PolicyRequest policyRequest) {
        // Find the client
        Client client = clientRepository.findById(policyRequest.getClientId())
                .orElseThrow(() -> new RuntimeException("Client not found"));
        
        // Check if policy number already exists
        if (policyRepository.existsByPolicyNumber(policyRequest.getPolicyNumber())) {
            throw new RuntimeException("Policy number already exists");
        }
        
        // Validate dates
        if (policyRequest.getExpiryDate().isBefore(policyRequest.getStartDate())) {
            throw new RuntimeException("Expiry date must be after start date");
        }
        
        // Create new policy
        Policy policy = new Policy();
        policy.setPolicyNumber(policyRequest.getPolicyNumber());
        policy.setPolicyType(policyRequest.getPolicyType());
        policy.setStartDate(policyRequest.getStartDate());
        policy.setExpiryDate(policyRequest.getExpiryDate());
        policy.setPremium(policyRequest.getPremium());
        policy.setPremiumFrequency(policyRequest.getPremiumFrequency());
        policy.setDescription(policyRequest.getDescription());
        policy.setStatus("ACTIVE");
        policy.setClient(client);
        
        // Save and return policy
        return policyRepository.save(policy);
    }
    
    /**
     * Get all policies for a client
     * @param clientId client ID
     * @return list of policies
     */
    public List<Policy> getPoliciesByClient(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));
        
        return policyRepository.findByClient(client);
    }
    
    /**
     * Get policy by ID
     * @param policyId policy ID
     * @return policy
     */
    public Policy getPolicyById(Long policyId) {
        return policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
    }
    
    /**
     * Get policies expiring on a specific date
     * Used by renewal scheduler
     * @param expiryDate the expiry date
     * @return list of policies expiring on that date
     */
    public List<Policy> getPoliciesExpiringOn(LocalDate expiryDate) {
        return policyRepository.findPoliciesExpiringOn(expiryDate);
    }
    
    /**
     * Update policy status
     * @param policyId policy ID
     * @param status new status
     * @return updated policy
     */
    @Transactional
    public Policy updatePolicyStatus(Long policyId, String status) {
        Policy policy = getPolicyById(policyId);
        policy.setStatus(status);
        return policyRepository.save(policy);
    }
}