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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Policy Service
 *
 * Handles all policy CRUD operations.
 *
 * PDF storage is handled by S3 via CloudStorageService.
 * The policy entity only stores the S3 URL in pdfFilePath —
 * no local file system is involved.
 *
 * When a policy is deleted, the S3 file is also deleted.
 */
@Service
public class PolicyService {

    private static final Logger logger = LoggerFactory.getLogger(PolicyService.class);

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private CloudStorageService cloudStorageService;

    /**
     * Create a new insurance policy with client details.
     *
     * If the client email already exists in the DB, their record is reused.
     * Otherwise a new client is created.
     *
     * The pdfFilePath field in the request is already a full S3 URL
     * set by PdfExtractionService — we just save it directly.
     */
    @Transactional
    public PolicyWithClientResponse createPolicyWithClient(PolicyWithClientRequest request, String username) {
        Agent agent = agentRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Agent not found"));

        // Find existing client by email or create a new one
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

        // Store S3 URL directly — no file moving, no temp folders
        if (request.getPdfFilePath() != null && !request.getPdfFilePath().isBlank()) {
            policy.setPdfFilePath(request.getPdfFilePath());
            logger.info("Policy {} linked to S3 file: {}", request.getPolicyNumber(), request.getPdfFilePath());
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
     * Also deletes the associated PDF from S3 if one exists.
     */
    @Transactional
    public void deletePolicy(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));

        // Delete PDF from S3 — CloudStorageService handles errors gracefully
        // so this will never cause the policy deletion to fail
        if (policy.getPdfFilePath() != null && !policy.getPdfFilePath().isBlank()) {
            cloudStorageService.deleteFile(policy.getPdfFilePath());
        }

        policyRepository.delete(policy);
    }

    /**
     * Get the raw Policy entity.
     * pdfFilePath is an S3 URL — the frontend can open it directly.
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