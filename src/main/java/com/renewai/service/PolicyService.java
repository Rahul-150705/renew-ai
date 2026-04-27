package com.renewai.service;

import com.renewai.dto.ConfirmRenewalRequest;
import com.renewai.dto.PolicyRequest;
import com.renewai.dto.PolicyWithClientRequest;
import com.renewai.dto.PolicyWithClientResponse;
import com.renewai.entity.Agent;
import com.renewai.entity.Client;
import com.renewai.entity.Policy;
import com.renewai.repository.AgentRepository;
import com.renewai.repository.ClientRepository;
import com.renewai.repository.MessageLogRepository;
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
    private MessageLogRepository messageLogRepository;

    // @Autowired  // PDF storage disabled
    // private CloudStorageService cloudStorageService;

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
                    newClient.setWhatsappNumber(request.getClientWhatsappNumber());
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
        policy.setPolicyType(request.getPolicyType() != null ? request.getPolicyType() : "VEHICLE");
        policy.setVehicleType(request.getVehicleType());
        policy.setRegistrationNumber(request.getRegistrationNumber());
        policy.setInsurerName(request.getInsurerName());
        policy.setStartDate(request.getStartDate());
        policy.setExpiryDate(request.getExpiryDate());
        policy.setPremium(request.getPremium());
        policy.setPremiumFrequency(request.getPremiumFrequency());
        policy.setDescription(request.getPolicyDescription());
        policy.setStatus("ACTIVE");
        policy.setClient(client);

        // PDF storage disabled — pdfFilePath not used
        // if (request.getPdfFilePath() != null && !request.getPdfFilePath().isBlank()) {
        //     policy.setPdfFilePath(request.getPdfFilePath());
        //     logger.info("Policy {} linked to S3 file: {}", request.getPolicyNumber(), request.getPdfFilePath());
        // }

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
     * Mark a policy as manually renewed or contacted.
     * Used when automated messaging fails after max retries and
     * the agent contacts the customer directly.
     *
     * @param policyId the policy ID
     * @param notes agent's notes about the manual contact
     * @param renewed whether the contact resulted in a renewal
     * @return updated policy with client information
     */
    @Transactional
    public PolicyWithClientResponse confirmAndRenew(Long oldPolicyId, ConfirmRenewalRequest request) {
        // 1. Get the old policy
        Policy oldPolicy = policyRepository.findById(oldPolicyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));

        Client client = oldPolicy.getClient();
        logger.info("Confirming renewal for policy: {} | Client: {}", 
                    oldPolicy.getPolicyNumber(), client.getFullName());

        // 2. Generate new policy number
        String newPolicyNumber = generateRenewedPolicyNumber(oldPolicy.getPolicyNumber());

        // 3. Create new policy copying old details
        Policy newPolicy = new Policy();
        newPolicy.setPolicyNumber(newPolicyNumber);
        newPolicy.setPolicyType(oldPolicy.getPolicyType());
        newPolicy.setVehicleType(oldPolicy.getVehicleType());
        newPolicy.setRegistrationNumber(oldPolicy.getRegistrationNumber());
        newPolicy.setInsurerName(oldPolicy.getInsurerName());
        newPolicy.setStartDate(request.getNewStartDate());
        newPolicy.setExpiryDate(request.getNewExpiryDate());
        
        // Use new premium if provided, else keep old
        newPolicy.setPremium(request.getNewPremium() != null ? 
                             request.getNewPremium() : oldPolicy.getPremium());
        
        newPolicy.setPremiumFrequency(oldPolicy.getPremiumFrequency());
        newPolicy.setDescription(oldPolicy.getDescription());
        newPolicy.setStatus("ACTIVE");
        newPolicy.setRenewalStatus("RENEWED");
        newPolicy.setManualRenewalNotes(request.getNotes());
        newPolicy.setClient(client);

        newPolicy = policyRepository.save(newPolicy);
        logger.info("New policy created: {}", newPolicyNumber);

        // 4. Delete old message logs first (foreign key constraint)
        messageLogRepository.deleteByPolicyId(oldPolicyId);
        logger.info("Deleted message logs for old policy ID: {}", oldPolicyId);

        // 5. Delete old policy
        policyRepository.delete(oldPolicy);
        logger.info("Deleted old policy: {}", oldPolicy.getPolicyNumber());

        return mapToResponse(newPolicy, client);
    }

    private String generateRenewedPolicyNumber(String oldPolicyNumber) {
        if (oldPolicyNumber.contains("-R")) {
            try {
                int lastDash = oldPolicyNumber.lastIndexOf("-R");
                String base = oldPolicyNumber.substring(0, lastDash);
                int renewalNum = Integer.parseInt(oldPolicyNumber.substring(lastDash + 2));
                String newNumber = base + "-R" + (renewalNum + 1);
                
                if (!policyRepository.existsByPolicyNumber(newNumber)) {
                    return newNumber;
                }
            } catch (Exception e) {
                // Fallback if parsing fails
            }
        }
        
        String newNumber = oldPolicyNumber + "-R1";
        if (!policyRepository.existsByPolicyNumber(newNumber)) {
            return newNumber;
        }
        
        return oldPolicyNumber + "-R" + System.currentTimeMillis();
    }

    /**
     * Mark a policy as manually renewed or contacted.
     * Used when automated messaging fails after max retries and
     * the agent contacts the customer directly.
     *
     * @param policyId the policy ID
     * @param notes agent's notes about the manual contact
     * @param renewed whether the contact resulted in a renewal
     * @return updated policy with client information
     */
    @Transactional
    public PolicyWithClientResponse markAsManuallyRenewed(Long policyId, String notes, boolean renewed) {
        logger.info("Marking policy {} as manually handled. Renewed: {}", policyId, renewed);
        
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found with id: " + policyId));

        if (renewed) {
            policy.setRenewalStatus("MANUAL_RENEWED");
            policy.setStatus("RENEWED");
            policy.setManualRenewalNotes(notes);
            policy = policyRepository.save(policy);
            
            // Resolve all failed messages for this policy
            messageLogRepository.resolveFailedMessagesByPolicy(policyId);
            
            logger.info("Policy {} manually renewed. Notes: {}", policy.getPolicyNumber(), notes);
            return mapToResponse(policy, policy.getClient());
        } else {
            // If not renewed, we lost the client, so delete the policy as per user request
            logger.info("Policy {} marked as NOT renewed. Deleting policy as client is lost.", policy.getPolicyNumber());
            
            // Delete directly instead of calling internal @Transactional method
            // PDF storage disabled — S3 deletion commented out
            // if (policy.getPdfFilePath() != null && !policy.getPdfFilePath().isBlank()) {
            //     cloudStorageService.deleteFile(policy.getPdfFilePath());
            // }
            
            policyRepository.delete(policy);
            
            // Return a dummy response to indicate deletion.
            PolicyWithClientResponse response = new PolicyWithClientResponse();
            response.setPolicyId(-1L);
            response.setPolicyStatus("DELETED");
            return response;
        }
    }

    /**
     * Delete a policy by ID.
     * Also deletes the associated PDF from S3 if one exists.
     */
    @Transactional
    public void deletePolicy(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));

        // PDF storage disabled — S3 deletion commented out
        // if (policy.getPdfFilePath() != null && !policy.getPdfFilePath().isBlank()) {
        //     cloudStorageService.deleteFile(policy.getPdfFilePath());
        // }

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
        response.setVehicleType(policy.getVehicleType());
        response.setRegistrationNumber(policy.getRegistrationNumber());
        response.setInsurerName(policy.getInsurerName());
        response.setStartDate(policy.getStartDate());
        response.setExpiryDate(policy.getExpiryDate());
        response.setPremium(policy.getPremium());
        response.setPremiumFrequency(policy.getPremiumFrequency());
        response.setPolicyDescription(policy.getDescription());
        response.setPolicyStatus(policy.getStatus());
        response.setRenewalStatus(policy.getRenewalStatus());
        response.setManualRenewalNotes(policy.getManualRenewalNotes());
        response.setCreatedAt(policy.getCreatedAt());
        response.setUpdatedAt(policy.getUpdatedAt());
        // response.setHasPdf(policy.getPdfFilePath() != null && !policy.getPdfFilePath().isBlank());  // PDF storage disabled
        response.setClientId(client.getId());
        response.setClientFullName(client.getFullName());
        response.setClientEmail(client.getEmail());
        response.setClientPhoneNumber(client.getPhoneNumber());
        response.setClientWhatsappNumber(client.getWhatsappNumber());
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

    public List<Policy> getAllPoliciesExpiringBefore(LocalDate date) {
        return policyRepository.findActivePoliciesExpiringBefore(date);
    }

    @Transactional
    public Policy savePolicy(Policy policy) {
        return policyRepository.save(policy);
    }
}