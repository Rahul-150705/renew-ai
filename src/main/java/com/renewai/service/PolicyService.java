package com.renewai.service;

import com.renewai.dto.PolicyDTO;
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
import java.util.stream.Collectors;

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
     * Convert Policy entity to DTO
     */
    private PolicyDTO convertToDTO(Policy policy) {
        PolicyDTO dto = new PolicyDTO();
        dto.setId(policy.getId());
        dto.setPolicyNumber(policy.getPolicyNumber());
        dto.setPolicyType(policy.getPolicyType());
        dto.setStartDate(policy.getStartDate());
        dto.setExpiryDate(policy.getExpiryDate());
        dto.setPremium(policy.getPremium());
        dto.setPremiumFrequency(policy.getPremiumFrequency());
        dto.setDescription(policy.getDescription());
        dto.setStatus(policy.getStatus());
        dto.setCreatedAt(policy.getCreatedAt());
        dto.setUpdatedAt(policy.getUpdatedAt());
        
        // Set client basic info
        Client client = policy.getClient();
        PolicyDTO.ClientBasicDTO clientDTO = new PolicyDTO.ClientBasicDTO();
        clientDTO.setId(client.getId());
        clientDTO.setFullName(client.getFullName());
        clientDTO.setEmail(client.getEmail());
        clientDTO.setPhoneNumber(client.getPhoneNumber());
        dto.setClient(clientDTO);
        
        return dto;
    }
    
    /**
     * Create a new insurance policy for a client
     * @param policyRequest policy details
     * @return created policy DTO
     */
    @Transactional
    public PolicyDTO createPolicy(PolicyRequest policyRequest) {
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
        
        // Save and return policy DTO
        Policy savedPolicy = policyRepository.save(policy);
        return convertToDTO(savedPolicy);
    }
    
    /**
     * Get all policies for a client
     * @param clientId client ID
     * @return list of policy DTOs
     */
    public List<PolicyDTO> getPoliciesByClient(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));
        
        List<Policy> policies = policyRepository.findByClient(client);
        return policies.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Get policy by ID
     * @param policyId policy ID
     * @return policy DTO
     */
    public PolicyDTO getPolicyById(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
        return convertToDTO(policy);
    }
    
    /**
     * Get policy entity by ID (for internal use)
     * @param policyId policy ID
     * @return policy entity
     */
    public Policy getPolicyEntityById(Long policyId) {
        return policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
    }
    
    /**
     * Get policies expiring on a specific date
     * Used by renewal scheduler
     * @param expiryDate the expiry date
     * @return list of policy entities
     */
    public List<Policy> getPoliciesExpiringOn(LocalDate expiryDate) {
        return policyRepository.findPoliciesExpiringOn(expiryDate);
    }
    
    /**
     * Update policy status
     * @param policyId policy ID
     * @param status new status
     * @return updated policy DTO
     */
    @Transactional
    public PolicyDTO updatePolicyStatus(Long policyId, String status) {
        Policy policy = getPolicyEntityById(policyId);
        policy.setStatus(status);
        Policy updatedPolicy = policyRepository.save(policy);
        return convertToDTO(updatedPolicy);
    }
}