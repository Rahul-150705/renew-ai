package com.renewai.service;

import com.renewai.entity.Agent;
import com.renewai.repository.AgentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class NLQueryService {

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqUrl;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String groqModel;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AgentRepository agentRepository;

    private static final String SCHEMA_PROMPT = """
        You are a SQL expert for a PostgreSQL insurance database.
        Given a natural language question, write ONE valid SELECT query.
        
        Rules:
        - Only write SELECT statements. Never INSERT, UPDATE, DELETE, DROP.
        - Always filter by agent_id = :agent_id for data isolation.
        - When searching by names or strings, use ILIKE '%searchTerm%' to be case-insensitive.
        - Do NOT use SELECT *. When asked for "all details", select only the 4-6 most relevant columns (e.g., full_name, phone_number, policy_number, status, premium) to prevent overwhelming raw text.
        - Return ONLY the SQL query. No explanation, no markdown, no backticks.
        
        Schema:
        
        agents(id, username, email, full_name, phone_number, active)
        
        clients(id, full_name, email, phone_number, whatsapp_number,
                address, agent_id, created_at)
        
        policies(id, policy_number, policy_type, vehicle_type,
                 registration_number, insurer_name, start_date, expiry_date,
                 premium, premium_frequency, description,
                 status,         -- ACTIVE | EXPIRED | RENEWED
                 renewal_status, -- PENDING | AUTO_RENEWED | MANUAL_RENEWED | RENEWED
                 manual_renewal_notes, client_id, created_at, updated_at)
        
        message_logs(id, policy_id, customer_id, reminder_type,
                     channel, recipient_phone, message_content,
                     status, retry_count, failure_reason, sent_at)
        
        Example questions and queries:
        Q: total revenue last month
        A: SELECT COALESCE(SUM(p.premium), 0) AS total_revenue
           FROM policies p JOIN clients c ON p.client_id = c.id
           WHERE c.agent_id = :agent_id
           AND p.created_at >= DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 month')
           AND p.created_at <  DATE_TRUNC('month', CURRENT_DATE);
        
        Q: how many policies expiring this week
        A: SELECT COUNT(*) AS expiring_count
           FROM policies p JOIN clients c ON p.client_id = c.id
           WHERE c.agent_id = :agent_id
           AND p.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7 days'
           AND p.status = 'ACTIVE';
        """;

    public String ask(String question, String username, java.util.List<Map<String, String>> history) {
        if (question == null || question.trim().isEmpty()) {
            return "Please provide a question or message.";
        }
        
        Agent agent = agentRepository.findByUsername(username)
            .orElse(null);
        
        if (agent == null) {
            return "Error: Agent profile not found for username: " + username;
        }
        Long agentId = agent.getId();

        // Step 1: Ask Groq to write the SQL
        String sql = generateSql(question, agentId, history);

        // Step 2: Validate + execute
        String rawResult;
        try {
            rawResult = executeQuery(sql, agentId);
        } catch (Exception e) {
            // Fallback: If SQL execution fails, return data-focused error
            return "I'm here to analyze your insurance data. Please ask about revenue, policies, or clients. (Technical detail: " + e.getMessage() + ")";
        }

        // Step 3: Ask Groq to phrase the result naturally
        return formatAnswer(question, rawResult);
    }

    private String generateSql(String question, Long agentId, java.util.List<Map<String, String>> history) {
        StringBuilder promptBuilder = new StringBuilder(SCHEMA_PROMPT);
        
        if (history != null && !history.isEmpty()) {
            promptBuilder.append("\n\nPrevious Conversation Context:\n");
            for (Map<String, String> msg : history) {
                promptBuilder.append(msg.get("role").toUpperCase()).append(": ").append(msg.get("content")).append("\n");
            }
        }
        
        promptBuilder.append("\n\nQuestion: ").append(question)
            .append("\nReplace :agent_id with the literal value ").append(agentId)
            .append("\nSQL:");

        return callGroq(promptBuilder.toString()).strip()
            .replaceAll("(?i)```sql", "")
            .replaceAll("```", "")
            .strip();
    }

    private String executeQuery(String sql, Long agentId) {
        // More robust extraction: Find the first instance of SELECT
        String cleanedSql = sql;
        int selectIdx = sql.toUpperCase().indexOf("SELECT");
        if (selectIdx != -1) {
            cleanedSql = sql.substring(selectIdx);
            // Also remove any trailing text if it's there
            int semicolonIdx = cleanedSql.indexOf(";");
            if (semicolonIdx != -1) {
                cleanedSql = cleanedSql.substring(0, semicolonIdx + 1);
            }
        }

        String upper = cleanedSql.trim().toUpperCase();
        if (!upper.startsWith("SELECT")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed. I received: " + (sql.length() > 50 ? sql.substring(0, 50) + "..." : sql));
        }
        
        if (!cleanedSql.contains(agentId.toString())) {
            throw new IllegalArgumentException("Query must be scoped to your agent ID.");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(cleanedSql);
        return rows.toString();
    }

    private String formatAnswer(String question, String rawResult) {
        String prompt = "The user asked: \"" + question + "\"\n" +
            "The database returned: " + rawResult + "\n" +
            "Answer in one clear, friendly sentence using Indian Rupee (₹) for money. " +
            "No SQL, no technical terms.";
        return callGroq(prompt).strip();
    }

    private String callGroq(String prompt) {
        System.out.println("--- CALLING GROQ API ---");
        System.out.println("Prompt length: " + prompt.length());
        try {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(10000);
            factory.setReadTimeout(30000);
            RestTemplate rest = new RestTemplate(factory);
            
            Map<String, Object> body = Map.of(
                "model", groqModel,
                "messages", List.of(
                    Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0
            );

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (groqApiKey == null || groqApiKey.trim().isEmpty()) {
                System.out.println("ERROR: Groq API Key is MISSING.");
                return "ERROR: Groq API key is not configured in the backend.";
            }
            headers.set("Authorization", "Bearer " + groqApiKey);
            headers.set("Content-Type", "application/json");

            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);

            Map response = rest.postForObject(groqUrl, entity, Map.class);
            if (response != null && response.containsKey("choices")) {
                List choices = (List) response.get("choices");
                if (!choices.isEmpty()) {
                    Map choice = (Map) choices.get(0);
                    Map message = (Map) choice.get("message");
                    return (String) message.get("content");
                }
            }
            return "ERROR: Received an empty or invalid response from Groq API.";
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return "ERROR: Groq API Error - " + e.getStatusCode() + ": " + e.getResponseBodyAsString();
        } catch (Exception e) {
            return "ERROR: Failed to communicate with Groq API - " + e.getMessage();
        }
    }
}
