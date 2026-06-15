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

@Service
public class PdfExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(PdfExtractionService.class);

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String groqModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public PolicyExtractionResponse extractPolicyData(MultipartFile file) throws IOException {
        logger.info("Starting PDF extraction for file: {}", file.getOriginalFilename());

        String pdfText = extractTextFromPdf(file);
        if (pdfText == null || pdfText.trim().isEmpty()) {
            return createErrorResponse("No text found in PDF");
        }

        logger.info("Extracted {} characters from PDF", pdfText.length());

        return extractDataUsingGroq(pdfText);
    }

    private String extractTextFromPdf(MultipartFile file) throws IOException {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private PolicyExtractionResponse extractDataUsingGroq(String pdfText) {
        try {
            String prompt = createExtractionPrompt(pdfText);
            String groqResponse = callGroqAPI(prompt);
            return parseGroqResponse(groqResponse);
        } catch (Exception e) {
            logger.error("Error using Groq API: {}", e.getMessage(), e);
            return createErrorResponse("Failed to extract data using AI: " + e.getMessage());
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
                    "policyType": "LIFE|HEALTH|VEHICLE|HOME|TRAVEL",
                    "vehicleType": "string (e.g., Car, Bike, Truck)",
                    "registrationNumber": "string (vehicle number plate / registration no)",
                    "insurerName": "string (name of the insurance company)",
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

    private String callGroqAPI(String prompt) throws Exception {
        String requestBody = objectMapper.writeValueAsString(new GroqRequest(
                groqModel,
                new GroqMessage[]{
                        new GroqMessage("system", "You extract structured data from insurance documents. Respond with valid JSON only."),
                        new GroqMessage("user", prompt)
                },
                0.3,
                2000
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(groqApiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + groqApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Groq API error: " + response.statusCode() + " - " + response.body());
        }
        return response.body();
    }

    private PolicyExtractionResponse parseGroqResponse(String groqResponse) throws Exception {
        JsonNode root = objectMapper.readTree(groqResponse);
        JsonNode choices = root.path("choices");
        if (choices.isEmpty()) throw new RuntimeException("No response from Groq");

        String extractedText = choices.get(0).path("message").path("content").asText()
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        JsonNode policyData;
        try {
            policyData = objectMapper.readTree(extractedText);
        } catch (Exception e) {
            throw new RuntimeException("Groq returned invalid JSON: " + e.getMessage());
        }

        PolicyExtractionResponse response = new PolicyExtractionResponse();
        response.setClientFullName(getJsonString(policyData, "clientFullName"));
        response.setClientEmail(getJsonString(policyData, "clientEmail"));
        response.setClientPhoneNumber(getJsonString(policyData, "clientPhoneNumber"));
        response.setClientAddress(getJsonString(policyData, "clientAddress"));
        response.setPolicyNumber(getJsonString(policyData, "policyNumber"));
        response.setPolicyType(getJsonString(policyData, "policyType"));
        response.setVehicleType(getJsonString(policyData, "vehicleType"));
        response.setRegistrationNumber(getJsonString(policyData, "registrationNumber"));
        response.setInsurerName(getJsonString(policyData, "insurerName"));
        response.setStartDate(getJsonString(policyData, "startDate"));
        response.setExpiryDate(getJsonString(policyData, "expiryDate"));
        response.setPremium(getJsonString(policyData, "premium"));
        response.setPremiumFrequency(getJsonString(policyData, "premiumFrequency"));
        response.setPolicyDescription(getJsonString(policyData, "policyDescription"));
        response.setSuccess(true);
        response.setMessage("Data extracted successfully using Groq AI");
        response.setConfidence(0.95);
        return response;
    }

    private String getJsonString(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) return null;
        String value = fieldNode.asText();
        return (value != null && !value.isEmpty() && !value.equalsIgnoreCase("null")) ? value : null;
    }

    private PolicyExtractionResponse createErrorResponse(String message) {
        PolicyExtractionResponse response = new PolicyExtractionResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setConfidence(0.0);
        return response;
    }

    private record GroqRequest(String model, GroqMessage[] messages, double temperature, int max_tokens) {}
    private record GroqMessage(String role, String content) {}
}