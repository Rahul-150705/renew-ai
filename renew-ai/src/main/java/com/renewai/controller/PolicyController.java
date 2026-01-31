package com.renewai.controller;

import com.renewai.dto.PolicyRequest;
import com.renewai.dto.PolicyWithClientRequest;
import com.renewai.dto.PolicyWithClientResponse;
import com.renewai.entity.Policy;
import com.renewai.service.PolicyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Policy Controller
 * UPDATED: Simplified to handle policy creation with client details
 * SECURED ENDPOINT - JWT required
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {
    
    @Autowired
    private PolicyService policyService;
    
    /**
     * Create a new insurance policy WITH client details
     * POST /api/policies/create
     * This endpoint creates both client (if new) and policy in one request
     * @param request policy and client details
     * @param authentication JWT authentication
     * @return created policy with client information
     */
    @PostMapping("/create")
    public ResponseEntity<?> createPolicyWithClient(
            @Valid @RequestBody PolicyWithClientRequest request,
            Authentication authentication) {
        
        try {
            String username = authentication.getName();
            PolicyWithClientResponse response = policyService.createPolicyWithClient(request, username);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * Get ALL policies for the authenticated agent
     * GET /api/policies
     * Returns all policies with client information
     * @param authentication JWT authentication
     * @return list of policies with client details
     */
    @GetMapping
    public ResponseEntity<List<PolicyWithClientResponse>> getAllMyPolicies(Authentication authentication) {
        String username = authentication.getName();
        List<PolicyWithClientResponse> policies = policyService.getAllPoliciesForAgent(username);
        return ResponseEntity.ok(policies);
    }
    
    /**
     * Get policy by ID with client information
     * GET /api/policies/{id}
     * @param id policy ID
     * @return policy with client details
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getPolicyById(@PathVariable Long id) {
        try {
            PolicyWithClientResponse policy = policyService.getPolicyWithClientById(id);
            return ResponseEntity.ok(policy);
            
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
    
    /**
     * Update policy status
     * PUT /api/policies/{id}/status
     * @param id policy ID
     * @param statusRequest status update request
     * @return updated policy with client details
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updatePolicyStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> statusRequest) {
        
        try {
            String status = statusRequest.get("status");
            if (status == null || status.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Status is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            PolicyWithClientResponse policy = policyService.updatePolicyStatus(id, status);
            return ResponseEntity.ok(policy);
            
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    // ===== KEEP OLD ENDPOINTS FOR BACKWARD COMPATIBILITY =====
    
    /**
     * OLD ENDPOINT: Create a new insurance policy (requires existing client)
     * POST /api/policies
     * Kept for backward compatibility
     * @param policyRequest policy details
     * @param authentication JWT authentication
     * @return created policy
     */
    @PostMapping
    public ResponseEntity<?> createPolicy(
            @Valid @RequestBody PolicyRequest policyRequest,
            Authentication authentication) {
        
        try {
            Policy policy = policyService.createPolicy(policyRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(policy);
            
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * Get all policies for a specific client
     * GET /api/policies/client/{clientId}
     * Kept for backward compatibility
     * @param clientId client ID
     * @return list of policies
     */
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<Policy>> getPoliciesByClient(@PathVariable Long clientId) {
        List<Policy> policies = policyService.getPoliciesByClient(clientId);
        return ResponseEntity.ok(policies);
    }
}