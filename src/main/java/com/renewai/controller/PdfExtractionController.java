package com.renewai.controller;

import com.renewai.dto.PolicyExtractionResponse;
import com.renewai.service.PdfExtractionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * PDF Extraction Controller
 * Handles PDF upload and data extraction for policy creation
 * SECURED ENDPOINT - JWT required
 */
@RestController
@RequestMapping("/api/policies")
public class PdfExtractionController {
    
    @Autowired
    private PdfExtractionService pdfExtractionService;
    
    /**
     * Extract policy data from uploaded PDF
     * POST /api/policies/extract-from-pdf
     * @param file PDF file containing policy information
     * @return extracted policy and client data
     */
    @PostMapping("/extract-from-pdf")
    public ResponseEntity<?> extractPolicyDataFromPdf(@RequestParam("file") MultipartFile file) {
        try {
            // Validate file
            if (file.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Please upload a file");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            // Check if file is PDF
            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals("application/pdf")) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Only PDF files are allowed");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            // Check file size (max 10MB)
            if (file.getSize() > 10 * 1024 * 1024) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "File size must be less than 10MB");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            // Extract data from PDF
            PolicyExtractionResponse extractedData = pdfExtractionService.extractPolicyData(file);
            
            return ResponseEntity.ok(extractedData);
            
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to extract data from PDF: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}