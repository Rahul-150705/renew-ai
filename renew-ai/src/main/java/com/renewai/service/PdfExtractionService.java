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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

/**
 * Service for extracting policy data from PDF documents
 * Uses Apache PDFBox for PDF parsing and ChatGPT API for intelligent data extraction
 * Stores uploaded PDF files for future reference
 */
@Service
public class PdfExtractionService {
    
    private static final Logger logger = LoggerFactory.getLogger(PdfExtractionService.class);
    
    @Value("${openai.api.key}")
    private String openaiApiKey;
    
    @Value("${pdf.storage.path:uploads/policies}")
    private String pdfStoragePath;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    /**
     * Extract policy data from PDF file using ChatGPT and store the file
     * @param file PDF file
     * @return extracted policy data with file path
     */
    public PolicyExtractionResponse extractPolicyData(MultipartFile file) throws IOException {
        logger.info("Starting PDF extraction for file: {}", file.getOriginalFilename());
        
        // Step 1: Extract text from PDF
        String pdfText = extractTextFromPdf(file);
        
        if (pdfText == null || pdfText.trim().isEmpty()) {
            return createErrorResponse("No text found in PDF");
        }
        
        logger.info("Extracted {} characters from PDF", pdfText.length());
        
        // Step 2: Store the PDF file
        String storedFilePath = null;
        try {
            storedFilePath = storePdfFile(file);
            logger.info("PDF file stored at: {}", storedFilePath);
        } catch (Exception e) {
            logger.error("Failed to store PDF file: {}", e.getMessage());
            // Continue with extraction even if storage fails
        }
        
        // Step 3: Use ChatGPT API to extract structured data
        PolicyExtractionResponse response = extractDataUsingChatGPT(pdfText);
        
        // Add the stored file path to the response
        if (storedFilePath != null) {
            String existingDesc = response.getPolicyDescription();
            response.setPolicyDescription(
                (existingDesc != null && !existingDesc.isEmpty() ? existingDesc + " | " : "") +
                "PDF File: " + storedFilePath
            );
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
     * Store PDF file in the file system
     * @param file PDF file to store
     * @return relative path to the stored file
     */
    private String storePdfFile(MultipartFile file) throws IOException {
        // Create storage directory if it doesn't exist
        Path storageDir = Paths.get(pdfStoragePath);
        if (!Files.exists(storageDir)) {
            Files.createDirectories(storageDir);
            logger.info("Created PDF storage directory: {}", storageDir.toAbsolutePath());
        }
        
        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename != null && originalFilename.contains(".") 
            ? originalFilename.substring(originalFilename.lastIndexOf("."))
            : ".pdf";
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
        
        // Save file
        Path targetPath = storageDir.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        
        // Return relative path
        return pdfStoragePath + File.separator + uniqueFilename;
    }
    
    /**
     * Extract structured data using ChatGPT API
     */
    private PolicyExtractionResponse extractDataUsingChatGPT(String pdfText) {
        try {
            String prompt = createExtractionPrompt(pdfText);
            String chatGPTResponse = callChatGPTAPI(prompt);
            
            // Parse ChatGPT's JSON response
            return parseChatGPTResponse(chatGPTResponse);
            
        } catch (Exception e) {
            logger.error("Error using ChatGPT API: {}", e.getMessage(), e);
            return createErrorResponse("Failed to extract data using AI: " + e.getMessage());
        }
    }
    
    /**
     * Create prompt for ChatGPT API
     */
    private String createExtractionPrompt(String pdfText) {
        // Limit text to first 4000 characters to stay within token limits
        String limitedText = pdfText.length() > 4000 
            ? pdfText.substring(0, 4000) + "..." 
            : pdfText;
            
        return String.format("""
            You are an expert at extracting insurance policy information from documents.
            
            Extract the following information from this insurance policy document and return ONLY a valid JSON object.
            Use null for any field you cannot find. Do not include any explanation or markdown formatting.
            
            Required JSON format:
            {
                "clientFullName": "string",
                "clientEmail": "string",
                "clientPhoneNumber": "string (E.164 format with country code, e.g., +919876543210)",
                "clientAddress": "string",
                "policyNumber": "string",
                "policyType": "LIFE|HEALTH|AUTO|HOME|TRAVEL",
                "startDate": "YYYY-MM-DD",
                "expiryDate": "YYYY-MM-DD",
                "premium": "number as string (just the number, no currency symbols)",
                "premiumFrequency": "MONTHLY|QUARTERLY|YEARLY",
                "policyDescription": "string (brief description of coverage)"
            }
            
            Document text:
            %s
            
            Remember: Return ONLY the JSON object, no other text or formatting.
            """, limitedText);
    }
    
    /**
     * Call ChatGPT API (OpenAI)
     */
    private String callChatGPTAPI(String prompt) throws Exception {
        // Create request body for ChatGPT API
        String requestBody = objectMapper.writeValueAsString(new ChatGPTRequest(
            "gpt-3.5-turbo",
            new ChatGPTMessage[]{
                new ChatGPTMessage("system", "You are a helpful assistant that extracts structured data from insurance documents. Always respond with valid JSON only, no markdown or explanation."),
                new ChatGPTMessage("user", prompt)
            },
            0.3, // Lower temperature for more consistent extraction
            2000  // Max tokens
        ));
        
        logger.debug("Calling ChatGPT API...");
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openaiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            logger.error("ChatGPT API error: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("ChatGPT API error: " + response.statusCode() + " - " + response.body());
        }
        
        logger.debug("ChatGPT API call successful");
        return response.body();
    }
    
    /**
     * Parse ChatGPT API response
     */
    private PolicyExtractionResponse parseChatGPTResponse(String chatGPTResponse) throws Exception {
        JsonNode root = objectMapper.readTree(chatGPTResponse);
        
        // Get the assistant's message content
        JsonNode choices = root.path("choices");
        if (choices.isEmpty()) {
            throw new RuntimeException("No response from ChatGPT");
        }
        
        String extractedText = choices.get(0).path("message").path("content").asText();
        logger.debug("Extracted text from ChatGPT: {}", extractedText);
        
        // Remove markdown code blocks if present
        extractedText = extractedText
            .replaceAll("```json\\s*", "")
            .replaceAll("```\\s*", "")
            .trim();
        
        // Parse the extracted JSON
        JsonNode policyData;
        try {
            policyData = objectMapper.readTree(extractedText);
        } catch (Exception e) {
            logger.error("Failed to parse ChatGPT response as JSON: {}", extractedText);
            throw new RuntimeException("ChatGPT returned invalid JSON: " + e.getMessage());
        }
        
        // Build response object
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
        response.setMessage("Data extracted successfully using ChatGPT AI");
        response.setConfidence(0.95); // High confidence for AI extraction
        
        logger.info("Successfully parsed policy data from ChatGPT response");
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
        String value = fieldNode.asText();
        return (value != null && !value.isEmpty() && !value.equalsIgnoreCase("null")) ? value : null;
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
    
    // Inner classes for ChatGPT API
    private record ChatGPTRequest(
        String model,
        ChatGPTMessage[] messages,
        double temperature,
        int max_tokens
    ) {}
    
    private record ChatGPTMessage(
        String role,
        String content
    ) {}
}