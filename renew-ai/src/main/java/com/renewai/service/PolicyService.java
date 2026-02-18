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

@Service
public class PolicyService {

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AgentRepository agentRepository;

    /**
     * Create a new insurance policy with client details.
     * Finds existing client by email or creates a new one.
     * Saves the pdfFilePath if provided.
     */
    @Transactional
    public PolicyWithClientResponse createPolicyWithClient(PolicyWithClientRequest request, String username) {
        Agent agent = agentRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Agent not found"));

        Client client = clientRepository.findByEmail(request.getClientEmail())
                .orElseGet(() -> {
                    Client newClient = new Client();
                    newClient.setFullName(request.getClientFullName());
                    newClient.setEmail(request.getClientEmail());
                    newClient.setPhoneNumber(request.getClientPhoneNumber());
                    newClient.setAddress(request.getClientAddress());
                    newClient.setAgent(agent);
                    return clientRepository.save(newClient);
                });

        if (policyRepository.existsByPolicyNumber(request.getPolicyNumber())) {
            throw new RuntimeException("Policy number already exists");
        }

        if (request.getExpiryDate().isBefore(request.getStartDate())) {
            throw new RuntimeException("Expiry date must be after start date");
        }

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

        // Save PDF path if provided from extraction
        if (request.getPdfFilePath() != null && !request.getPdfFilePath().isBlank()) {
            policy.setPdfFilePath(request.getPdfFilePath());
        }

        policy = policyRepository.save(policy);
        return mapToResponse(policy, client);
    }

    /**
     * Get all policies for the authenticated agent.
     */
    public List<PolicyWithClientResponse> getAllPoliciesForAgent(String username) {
        Agent agent = agentRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Agent not found"));

        List<Client> clients = clientRepository.findByAgent(agent);

        return clients.stream()
                .flatMap(client -> policyRepository.findByClient(client).stream()
                        .map(policy -> mapToResponse(policy, client)))
                .collect(Collectors.toList());
    }

    /**
     * Get policy by ID with client information.
     */
    public PolicyWithClientResponse getPolicyWithClientById(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
        return mapToResponse(policy, policy.getClient());
    }

    /**
     * Update policy status.
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
     * Delete a policy by ID.
     */
    @Transactional
    public void deletePolicy(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
        policyRepository.delete(policy);
    }

    /**
     * Get the raw Policy entity (used by PDF download endpoint).
     */
    public Policy getPolicyEntityById(Long policyId) {
        return policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
    }

    private PolicyWithClientResponse mapToResponse(Policy policy, Client client) {
        PolicyWithClientResponse response = new PolicyWithClientResponse();
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
        response.setHasPdf(policy.getPdfFilePath() != null && !policy.getPdfFilePath().isBlank());
        response.setClientId(client.getId());
        response.setClientFullName(client.getFullName());
        response.setClientEmail(client.getEmail());
        response.setClientPhoneNumber(client.getPhoneNumber());
        response.setClientAddress(client.getAddress());
        return response;
    }

    // ===== BACKWARD COMPATIBILITY =====

    @Transactional
    public Policy createPolicy(PolicyRequest policyRequest) {
        Client client = clientRepository.findById(policyRequest.getClientId())
                .orElseThrow(() -> new RuntimeException("Client not found"));

        if (policyRepository.existsByPolicyNumber(policyRequest.getPolicyNumber())) {
            throw new RuntimeException("Policy number already exists");
        }

        if (policyRequest.getExpiryDate().isBefore(policyRequest.getStartDate())) {
            throw new RuntimeException("Expiry date must be after start date");
        }

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

        return policyRepository.save(policy);
    }

    public List<Policy> getPoliciesByClient(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));
        return policyRepository.findByClient(client);
    }

    public Policy getPolicyById(Long policyId) {
        return policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
    }

    public List<Policy> getPoliciesExpiringOn(LocalDate expiryDate) {
        return policyRepository.findPoliciesExpiringOn(expiryDate);
    }
}