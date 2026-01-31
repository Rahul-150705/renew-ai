package com.renewai.service;

import com.renewai.dto.PolicyRequest;
import com.renewai.dto.PolicyWithClientRequest;
import com.renewai.dto.PolicyWithClientResponse;
import com.renewai.entity.Agent;
import com.renewai.entity.Client;
import com.renewai.entity.Policy;
import com.renewai.repository.AgentRepository;
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
 * UPDATED: Now supports creating policies with client details in one request
 */
@Service
public class PolicyService {
    
    @Autowired
    private PolicyRepository policyRepository;
    
    @Autowired
    private ClientRepository clientRepository;
    
    @Autowired
    private AgentRepository agentRepository;
    
    /**
     * Create a new insurance policy with client details
     * This method will either find existing client by email or create a new one
     * @param request policy and client details
     * @param username username of the agent creating the policy
     * @return policy response with client information
     */
    @Transactional
    public PolicyWithClientResponse createPolicyWithClient(PolicyWithClientRequest request, String username) {
        // Find the agent
        Agent agent = agentRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        
        // Find or create client
        Client client = clientRepository.findByEmail(request.getClientEmail())
                .orElseGet(() -> {
                    // Create new client
                    Client newClient = new Client();
                    newClient.setFullName(request.getClientFullName());
                    newClient.setEmail(request.getClientEmail());
                    newClient.setPhoneNumber(request.getClientPhoneNumber());
                    newClient.setAddress(request.getClientAddress());
                    newClient.setAgent(agent);
                    return clientRepository.save(newClient);
                });
        
        // Check if policy number already exists
        if (policyRepository.existsByPolicyNumber(request.getPolicyNumber())) {
            throw new RuntimeException("Policy number already exists");
        }
        
        // Validate dates
        if (request.getExpiryDate().isBefore(request.getStartDate())) {
            throw new RuntimeException("Expiry date must be after start date");
        }
        
        // Create new policy
        Policy policy = new Policy();
        policy.setPolicyNumber(request.getPolicyNumber());
        policy.setPolicyType(request.getPolicyType());
        policy.setStartDate(request.getStartDate());
        policy.setExpiryDate(request.getExpiryDate());
        policy.setPremium(request.getPremium());
        policy.setPremiumFrequency(request.getPremiumFrequency());
        policy.setDescription(request.getPolicyDescription());
        policy.setStatus("ACTIVE");
        policy.setClient(client);
        
        // Save policy
        policy = policyRepository.save(policy);
        
        // Return response DTO
        return mapToResponse(policy, client);
    }
    
    /**
     * Get all policies for the authenticated agent
     * @param username username of the agent
     * @return list of policy responses with client information
     */
    public List<PolicyWithClientResponse> getAllPoliciesForAgent(String username) {
        // Find the agent
        Agent agent = agentRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        
        // Get all clients for this agent
        List<Client> clients = clientRepository.findByAgent(agent);
        
        // Get all policies for these clients
        return clients.stream()
                .flatMap(client -> policyRepository.findByClient(client).stream()
                        .map(policy -> mapToResponse(policy, client)))
                .collect(Collectors.toList());
    }
    
    /**
     * Get policy by ID with client information
     * @param policyId policy ID
     * @return policy response with client information
     */
    public PolicyWithClientResponse getPolicyWithClientById(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
        
        return mapToResponse(policy, policy.getClient());
    }
    
    /**
     * Update policy status
     * @param policyId policy ID
     * @param status new status
     * @return updated policy response
     */
    @Transactional
    public PolicyWithClientResponse updatePolicyStatus(Long policyId, String status) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
        
        policy.setStatus(status);
        policy = policyRepository.save(policy);
        
        return mapToResponse(policy, policy.getClient());
    }
    
    /**
     * Helper method to map Policy entity to PolicyWithClientResponse DTO
     */
    private PolicyWithClientResponse mapToResponse(Policy policy, Client client) {
        PolicyWithClientResponse response = new PolicyWithClientResponse();
        
        // Policy details
        response.setPolicyId(policy.getId());
        response.setPolicyNumber(policy.getPolicyNumber());
        response.setPolicyType(policy.getPolicyType());
        response.setStartDate(policy.getStartDate());
        response.setExpiryDate(policy.getExpiryDate());
        response.setPremium(policy.getPremium());
        response.setPremiumFrequency(policy.getPremiumFrequency());
        response.setPolicyDescription(policy.getDescription());
        response.setPolicyStatus(policy.getStatus());
        response.setCreatedAt(policy.getCreatedAt());
        response.setUpdatedAt(policy.getUpdatedAt());
        
        // Client details
        response.setClientId(client.getId());
        response.setClientFullName(client.getFullName());
        response.setClientEmail(client.getEmail());
        response.setClientPhoneNumber(client.getPhoneNumber());
        response.setClientAddress(client.getAddress());
        
        return response;
    }
    
    // ===== KEEP EXISTING METHODS FOR BACKWARD COMPATIBILITY =====
    
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
}