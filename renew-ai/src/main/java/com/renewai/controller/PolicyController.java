package com.renewai.controller;

import com.renewai.dto.PolicyRequest;
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
 * Handles insurance policy management operations
 * SECURED ENDPOINT - JWT required
 */
@RestController
@RequestMapping("/api/policies")
@CrossOrigin(origins = "*")
public class PolicyController {
    
    @Autowired
    private PolicyService policyService;
    
    /**
     * Create a new insurance policy
     * POST /api/policies
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
     * Get all policies for a client
     * GET /api/policies/client/{clientId}
     * @param clientId client ID
     * @return list of policies
     */
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<Policy>> getPoliciesByClient(@PathVariable Long clientId) {
        List<Policy> policies = policyService.getPoliciesByClient(clientId);
        return ResponseEntity.ok(policies);
    }
    
    /**
     * Get policy by ID
     * GET /api/policies/{id}
     * @param id policy ID
     * @return policy details
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getPolicyById(@PathVariable Long id) {
        try {
            Policy policy = policyService.getPolicyById(id);
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
     * @return updated policy
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
            
            Policy policy = policyService.updatePolicyStatus(id, status);
            return ResponseEntity.ok(policy);
            
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
}