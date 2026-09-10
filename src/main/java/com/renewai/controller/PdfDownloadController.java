package com.renewai.controller;

import com.renewai.service.PolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/policies")
public class PdfDownloadController {

    @Autowired private PolicyService policyService;

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Map<String, String>> viewPolicyPdf(
            @PathVariable Long id,
            Authentication authentication) {
        String url = policyService.getPolicyPdfUrl(id, authentication.getName());
        return ResponseEntity.ok(Map.of("url", url));
    }
}