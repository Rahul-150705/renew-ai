package com.renewai.controller;

import com.renewai.dto.ConfirmRenewalRequest;
import com.renewai.dto.ManualRenewalRequest;
import com.renewai.dto.PolicyWithClientRequest;
import com.renewai.dto.PolicyWithClientResponse;
import com.renewai.service.CloudStorageService;
import com.renewai.service.PolicyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;
import com.renewai.dto.PolicyExtractionResponse;
import com.renewai.service.PdfExtractionService;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    @Autowired
    private PolicyService policyService;

    @Autowired
    private PdfExtractionService pdfExtractionService;

    @GetMapping
    public ResponseEntity<List<PolicyWithClientResponse>> getAllPolicies(Authentication authentication) {
        return ResponseEntity.ok(policyService.getAllPoliciesForAgent(authentication.getName()));
    }

    @PostMapping("/create")
    public ResponseEntity<PolicyWithClientResponse> createPolicy(
            @Valid @RequestBody PolicyWithClientRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(policyService.createPolicyWithClient(request, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PolicyWithClientResponse> getPolicyById(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(policyService.getPolicyWithClientById(id, authentication.getName()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<PolicyWithClientResponse> updatePolicyStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> statusRequest,
            Authentication authentication) {
        return ResponseEntity.ok(
                policyService.updatePolicyStatus(id, statusRequest.get("status"), authentication.getName()));
    }

    @PostMapping("/{id}/manual-renew")
    public ResponseEntity<PolicyWithClientResponse> markAsManuallyRenewed(
            @PathVariable Long id,
            @RequestBody ManualRenewalRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(policyService.markAsManuallyRenewed(
                id, request.getNotes(), request.isRenewed(), authentication.getName()));
    }

    @PostMapping("/{id}/confirm-renewal")
    public ResponseEntity<PolicyWithClientResponse> confirmRenewal(
            @PathVariable Long id,
            @RequestBody ConfirmRenewalRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(policyService.confirmAndRenew(id, request, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePolicy(@PathVariable Long id, Authentication authentication) {
        policyService.deletePolicy(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * Extract policy/client fields from an uploaded PDF for form auto-fill.
     * Does not store anything.
     */
    @PostMapping("/extract-from-pdf")
    public ResponseEntity<PolicyExtractionResponse> extractFromPdf(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(pdfExtractionService.extractPolicyData(file));
        } catch (Exception e) {
            PolicyExtractionResponse errorResponse = new PolicyExtractionResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Error extracting PDF: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Attach / replace the stored PDF document for a policy.
     * The file is uploaded to S3 under the owning agent's prefix.
     * Only the agent that owns the policy may call this.
     */
    @PostMapping(value = "/{id}/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PolicyWithClientResponse> uploadPolicyPdf(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(policyService.attachPdf(id, file, authentication.getName()));
    }

    /**
     * Stream the stored PDF for a policy back to the browser.
     * Returns 404 unless the policy belongs to the authenticated agent and has a document.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> viewPolicyPdf(@PathVariable Long id, Authentication authentication) {
        CloudStorageService.StoredFile stored = policyService.getPolicyPdf(id, authentication.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"policy-" + id + ".pdf\"")
                .body(stored.bytes());
    }
}
