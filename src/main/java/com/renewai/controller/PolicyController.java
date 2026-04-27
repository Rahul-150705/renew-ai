package com.renewai.controller;

import com.renewai.dto.ConfirmRenewalRequest;
import com.renewai.dto.ManualRenewalRequest;
import com.renewai.dto.PolicyWithClientRequest;
import com.renewai.dto.PolicyWithClientResponse;
import com.renewai.service.PolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policies")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PolicyController {

    @Autowired
    private PolicyService policyService;

    @GetMapping
    public ResponseEntity<List<PolicyWithClientResponse>> getAllPolicies(Authentication authentication) {
        return ResponseEntity.ok(policyService.getAllPoliciesForAgent(authentication.getName()));
    }

    @GetMapping("/debug/my-policies")
    public ResponseEntity<?> debugMyPolicies(Authentication authentication) {
        return ResponseEntity.ok(policyService.debugMyPolicies(authentication.getName()));
    }

    @PostMapping("/create")
    public ResponseEntity<PolicyWithClientResponse> createPolicy(
            @RequestBody PolicyWithClientRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(policyService.createPolicyWithClient(request, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PolicyWithClientResponse> getPolicyById(@PathVariable Long id) {
        return ResponseEntity.ok(policyService.getPolicyWithClientById(id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<PolicyWithClientResponse> updatePolicyStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> statusRequest) {
        return ResponseEntity.ok(policyService.updatePolicyStatus(id, statusRequest.get("status")));
    }

    @PostMapping("/{id}/manual-renew")
    public ResponseEntity<PolicyWithClientResponse> markAsManuallyRenewed(
            @PathVariable Long id,
            @RequestBody ManualRenewalRequest request) {
        return ResponseEntity.ok(policyService.markAsManuallyRenewed(id, request.getNotes(), request.isRenewed()));
    }

    @PostMapping("/{id}/confirm-renewal")
    public ResponseEntity<PolicyWithClientResponse> confirmRenewal(
            @PathVariable Long id,
            @RequestBody ConfirmRenewalRequest request) {
        return ResponseEntity.ok(policyService.confirmAndRenew(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePolicy(@PathVariable Long id) {
        policyService.deletePolicy(id);
        return ResponseEntity.ok().build();
    }
}