package com.renewai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renewai.dto.PolicyExtractionResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for extracting policy data from PDF documents
 * Uses Apache PDFBox for PDF parsing and Claude API for intelligent data extraction
 */
@Service
public class PdfExtractionService {
    
    private static final Logger logger = LoggerFactory.getLogger(PdfExtractionService.class);
    
    @Value("${claude.api.key:}")
    private String claudeApiKey;
    
    @Value("${claude.api.enabled:false}")
    private boolean claudeApiEnabled;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    /**
     * Extract policy data from PDF file
     * @param file PDF file
     * @return extracted policy data
     */
    public PolicyExtractionResponse extractPolicyData(MultipartFile file) throws IOException {
        logger.info("Starting PDF extraction for file: {}", file.getOriginalFilename());
        
        // Step 1: Extract text from PDF
        String pdfText = extractTextFromPdf(file);
        
        if (pdfText == null || pdfText.trim().isEmpty()) {
            return createErrorResponse("No text found in PDF");
        }
        
        logger.info("Extracted {} characters from PDF", pdfText.length());
        
        // Step 2: Use AI to extract structured data
        PolicyExtractionResponse response;
        if (claudeApiEnabled && claudeApiKey != null && !claudeApiKey.isEmpty()) {
            response = extractDataUsingClaude(pdfText);
        } else {
            // Fallback: Use regex-based extraction
            logger.warn("Claude API not configured, using regex-based extraction");
            response = extractDataUsingRegex(pdfText);
        }
        
        return response;
    }
    
    /**
     * Extract text from PDF using Apache PDFBox
     */
    private String extractTextFromPdf(MultipartFile file) throws IOException {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

        
    /**
     * Extract structured data using Claude API
     */
    private PolicyExtractionResponse extractDataUsingClaude(String pdfText) {
        try {
            String prompt = createExtractionPrompt(pdfText);
            String claudeResponse = callClaudeAPI(prompt);
            
            // Parse Claude's JSON response
            return parseClaudeResponse(claudeResponse);
            
        } catch (Exception e) {
            logger.error("Error using Claude API: {}", e.getMessage());
            // Fallback to regex
            return extractDataUsingRegex(pdfText);
        }
    }
    
    /**
     * Create prompt for Claude API
     */
    private String createExtractionPrompt(String pdfText) {
        return String.format("""
            Extract insurance policy information from the following document text.
            Return ONLY a valid JSON object with these exact fields (use null for missing values):
            
            {
                "clientFullName": "string",
                "clientEmail": "string",
                "clientPhoneNumber": "string (E.164 format with country code)",
                "clientAddress": "string",
                "policyNumber": "string",
                "policyType": "LIFE|HEALTH|AUTO|HOME|TRAVEL",
                "startDate": "YYYY-MM-DD",
                "expiryDate": "YYYY-MM-DD",
                "premium": "number as string",
                "premiumFrequency": "MONTHLY|QUARTERLY|YEARLY",
                "policyDescription": "string"
            }
            
            Document text:
            %s
            
            Return only the JSON object, no other text.
            """, pdfText.substring(0, Math.min(pdfText.length(), 3000))); // Limit to 3000 chars
    }
    
    /**
     * Call Claude API
     */
    private String callClaudeAPI(String prompt) throws Exception {
        String requestBody = objectMapper.writeValueAsString(new ClaudeRequest(
            "claude-sonnet-4-20250514",
            1024,
            new Message[]{new Message("user", prompt)}
        ));
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", claudeApiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API error: " + response.body());
        }
        
        return response.body();
    }
    
    /**
     * Parse Claude API response
     */
    private PolicyExtractionResponse parseClaudeResponse(String claudeResponse) throws Exception {
        JsonNode root = objectMapper.readTree(claudeResponse);
        JsonNode content = root.path("content").get(0);
        String extractedText = content.path("text").asText();
        
        // Remove markdown code blocks if present
        extractedText = extractedText.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
        
        // Parse the extracted JSON
        JsonNode policyData = objectMapper.readTree(extractedText);
        
        PolicyExtractionResponse response = new PolicyExtractionResponse();
        response.setClientFullName(getJsonString(policyData, "clientFullName"));
        response.setClientEmail(getJsonString(policyData, "clientEmail"));
        response.setClientPhoneNumber(getJsonString(policyData, "clientPhoneNumber"));
        response.setClientAddress(getJsonString(policyData, "clientAddress"));
        response.setPolicyNumber(getJsonString(policyData, "policyNumber"));
        response.setPolicyType(getJsonString(policyData, "policyType"));
        response.setStartDate(getJsonString(policyData, "startDate"));
        response.setExpiryDate(getJsonString(policyData, "expiryDate"));
        response.setPremium(getJsonString(policyData, "premium"));
        response.setPremiumFrequency(getJsonString(policyData, "premiumFrequency"));
        response.setPolicyDescription(getJsonString(policyData, "policyDescription"));
        response.setSuccess(true);
        response.setMessage("Data extracted successfully using AI");
        response.setConfidence(0.9);
        
        return response;
    }
    
    /**
     * Helper to safely get string from JSON
     */
    private String getJsonString(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asText();
    }
    
    /**
     * Fallback: Extract data using regex patterns
     */
    private PolicyExtractionResponse extractDataUsingRegex(String text) {
        PolicyExtractionResponse response = new PolicyExtractionResponse();
        
        // Extract client name
        response.setClientFullName(extractPattern(text, 
            "(?i)(name|insured|policyholder)\\s*:?\\s*([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+)"));
        
        // Extract email
        response.setClientEmail(extractPattern(text,
            "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"));
        
        // Extract phone
        response.setClientPhoneNumber(extractPattern(text,
            "(\\+?\\d{1,3}[\\s-]?\\(?\\d{3}\\)?[\\s-]?\\d{3}[\\s-]?\\d{4})"));
        
        // Extract policy number
        response.setPolicyNumber(extractPattern(text,
            "(?i)policy\\s*(?:number|no\\.?|#)\\s*:?\\s*([A-Z0-9-]+)"));
        
        // Extract dates
        String datePattern = "(\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4}|\\d{2}-\\d{2}-\\d{4})";
        response.setStartDate(extractPattern(text, 
            "(?i)(?:start|effective|from)\\s*(?:date)?\\s*:?\\s*" + datePattern));
        response.setExpiryDate(extractPattern(text,
            "(?i)(?:expiry|expiration|end|to)\\s*(?:date)?\\s*:?\\s*" + datePattern));
        
        // Extract premium
        response.setPremium(extractPattern(text,
            "(?i)premium\\s*:?\\s*(?:₹|Rs\\.?|USD)?\\s*([\\d,]+(?:\\.\\d{2})?)"));
        
        // Extract policy type
        String policyType = extractPattern(text,
            "(?i)(life|health|auto|home|travel)\\s+insurance");
        if (policyType != null) {
            response.setPolicyType(policyType.toUpperCase());
        }
        
        // Set metadata
        response.setSuccess(true);
        response.setMessage("Data extracted using pattern matching");
        response.setConfidence(0.6);
        
        return response;
    }
    
    /**
     * Helper to extract pattern from text
     */
    private String extractPattern(String text, String patternStr) {
        try {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                int groupCount = matcher.groupCount();
                return matcher.group(groupCount > 1 ? 2 : 1).trim();
            }
        } catch (Exception e) {
            logger.debug("Pattern match failed for: {}", patternStr);
        }
        return null;
    }
    
    /**
     * Create error response
     */
    private PolicyExtractionResponse createErrorResponse(String message) {
        PolicyExtractionResponse response = new PolicyExtractionResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setConfidence(0.0);
        return response;
    }
    
    // Inner classes for Claude API
    private record ClaudeRequest(String model, int max_tokens, Message[] messages) {}
    private record Message(String role, String content) {}
}