package com.renewai.controller;

import com.renewai.entity.Policy;
import com.renewai.service.PolicyService;
import com.renewai.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/policies")
public class PdfDownloadController {

    @Autowired private PolicyService policyService;
    @Autowired private S3Service s3Service;

    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> viewPolicyPdf(@PathVariable Long id) {
        Policy policy = policyService.getPolicyEntityById(id);
        if (policy.getPdfFilePath() == null || policy.getPdfFilePath().isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "No PDF file found for this policy"));
        }
        String url = s3Service.presignedGetUrl(policy.getPdfFilePath());
        return ResponseEntity.ok(Map.of("url", url));
    }
}