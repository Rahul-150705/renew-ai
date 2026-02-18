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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

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
     * Extract policy data from PDF and store the file.
     * Returns extracted data + storedFilePath so it can be linked to the policy on save.
     */
    public PolicyExtractionResponse extractPolicyData(MultipartFile file) throws IOException {
        logger.info("Starting PDF extraction for file: {}", file.getOriginalFilename());

        // Store PDF first so it's saved even if extraction fails
        String storedFilePath = storePdfFile(file);
        logger.info("PDF stored at: {}", storedFilePath);

        // Extract raw text
        String pdfText = extractTextFromPdf(file);
        if (pdfText == null || pdfText.trim().isEmpty()) {
            return createErrorResponse("No text found in PDF", storedFilePath);
        }

        logger.info("Extracted {} characters from PDF", pdfText.length());

        // Use ChatGPT to extract structured data
        PolicyExtractionResponse response = extractDataUsingChatGPT(pdfText);
        response.setStoredFilePath(storedFilePath);
        return response;
    }

    /**
     * Save the PDF to disk with a unique filename.
     */
    private String storePdfFile(MultipartFile file) throws IOException {
        Path storageDir = Paths.get(pdfStoragePath);
        if (!Files.exists(storageDir)) {
            Files.createDirectories(storageDir);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".pdf";

        String uniqueFilename = UUID.randomUUID() + extension;
        Path targetPath = storageDir.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        return pdfStoragePath + "/" + uniqueFilename;
    }

    /**
     * Extract text from PDF bytes using Apache PDFBox.
     */
    private String extractTextFromPdf(MultipartFile file) throws IOException {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private PolicyExtractionResponse extractDataUsingChatGPT(String pdfText) {
        try {
            String prompt = createExtractionPrompt(pdfText);
            String chatGPTResponse = callChatGPTAPI(prompt);
            return parseChatGPTResponse(chatGPTResponse);
        } catch (Exception e) {
            logger.error("Error using ChatGPT API: {}", e.getMessage(), e);
            return createErrorResponse("Failed to extract data using AI: " + e.getMessage(), null);
        }
    }

    private String createExtractionPrompt(String pdfText) {
        String limitedText = pdfText.length() > 4000
                ? pdfText.substring(0, 4000) + "..."
                : pdfText;

        return String.format("""
                You are an expert at extracting insurance policy information from documents.
                
                Extract the following information and return ONLY a valid JSON object.
                Use null for any field you cannot find. No explanation or markdown.
                
                {
                    "clientFullName": "string",
                    "clientEmail": "string",
                    "clientPhoneNumber": "string (E.164 format, e.g. +919876543210)",
                    "clientAddress": "string",
                    "policyNumber": "string",
                    "policyType": "LIFE|HEALTH|AUTO|HOME|TRAVEL",
                    "startDate": "YYYY-MM-DD",
                    "expiryDate": "YYYY-MM-DD",
                    "premium": "number as string (no currency symbols)",
                    "premiumFrequency": "MONTHLY|QUARTERLY|YEARLY",
                    "policyDescription": "string"
                }
                
                Document text:
                %s
                """, limitedText);
    }

    private String callChatGPTAPI(String prompt) throws Exception {
        String requestBody = objectMapper.writeValueAsString(new ChatGPTRequest(
                "gpt-3.5-turbo",
                new ChatGPTMessage[]{
                        new ChatGPTMessage("system", "You extract structured data from insurance documents. Respond with valid JSON only."),
                        new ChatGPTMessage("user", prompt)
                },
                0.3,
                2000
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openaiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("ChatGPT API error: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    private PolicyExtractionResponse parseChatGPTResponse(String chatGPTResponse) throws Exception {
        JsonNode root = objectMapper.readTree(chatGPTResponse);
        JsonNode choices = root.path("choices");
        if (choices.isEmpty()) throw new RuntimeException("No response from ChatGPT");

        String extractedText = choices.get(0).path("message").path("content").asText()
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        JsonNode policyData;
        try {
            policyData = objectMapper.readTree(extractedText);
        } catch (Exception e) {
            throw new RuntimeException("ChatGPT returned invalid JSON: " + e.getMessage());
        }

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
        response.setConfidence(0.95);
        return response;
    }

    private String getJsonString(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) return null;
        String value = fieldNode.asText();
        return (value != null && !value.isEmpty() && !value.equalsIgnoreCase("null")) ? value : null;
    }

    private PolicyExtractionResponse createErrorResponse(String message, String storedFilePath) {
        PolicyExtractionResponse response = new PolicyExtractionResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setConfidence(0.0);
        response.setStoredFilePath(storedFilePath);
        return response;
    }

    private record ChatGPTRequest(String model, ChatGPTMessage[] messages, double temperature, int max_tokens) {}
    private record ChatGPTMessage(String role, String content) {}
}