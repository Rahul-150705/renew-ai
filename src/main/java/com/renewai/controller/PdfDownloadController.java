package com.renewai.controller;

import com.renewai.entity.Policy;
import com.renewai.service.PolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller to serve stored PDF files for policies.
 * GET /api/policies/{id}/pdf  → streams the PDF back to the browser
 */
// @RestController  // PDF storage disabled
// @RequestMapping("/api/policies")
public class PdfDownloadController {

    @Autowired
    private PolicyService policyService;

    /**
     * Download / view the PDF attached to a policy.
     * Returns 404 if the policy has no PDF stored.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> downloadPolicyPdf(@PathVariable Long id) {
        try {
            Policy policy = policyService.getPolicyEntityById(id);

            if (policy.getPdfFilePath() == null || policy.getPdfFilePath().isBlank()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "No PDF file found for this policy");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            Path filePath = Paths.get(policy.getPdfFilePath()).toAbsolutePath();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "PDF file not accessible");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    // "inline" opens in browser tab; change to "attachment" to force download
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"policy-" + policy.getPolicyNumber() + ".pdf\"")
                    .body(resource);

        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to retrieve PDF: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}