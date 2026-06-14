package com.renewai.service;

import com.renewai.entity.Agent;
import com.renewai.repository.AgentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Natural Language Query Service — 7-Level Architecture
 *
 * Level 1 — Full conversation history sent to Groq (not just system+user)
 * Level 2 — Comprehensive system prompt with business rules
 * Level 3 — Session memory (lastTopic, lastCategories, lastResult, lastSql)
 * Level 4 — Intent + reference detection ("this", "those", "them")
 * Level 5 — Semantic category synonym mapping (car→Auto, bike→Bike, etc.)
 * Level 6 — Schema with business meaning (not just column names)
 * Level 7 — Conversation summarization when history grows large
 */
@Service
public class NLQueryService {

    private static final Logger logger = LoggerFactory.getLogger(NLQueryService.class);

    // ── Return type ───────────────────────────────────────────────────────────
    public record AskResult(String answer, String sql, Object data, SessionMemory sessionMemory) {
    }

    /**
     * Level 3 — Session Memory
     * Passed back to frontend after each response, sent back on next request.
     */
    public record SessionMemory(
            String lastTopic,
            List<String> lastCategories,
            String lastResultSummary,
            String lastSql) {
    }

    // ── Level 5 — Synonym Map ─────────────────────────────────────────────────
    private static final Map<String, String> CATEGORY_SYNONYMS = new LinkedHashMap<>();
    static {
        CATEGORY_SYNONYMS.put("car", "Auto");
        CATEGORY_SYNONYMS.put("automobile", "Auto");
        CATEGORY_SYNONYMS.put("four wheeler", "Auto");
        CATEGORY_SYNONYMS.put("four-wheeler", "Auto");
        CATEGORY_SYNONYMS.put("motorbike", "Bike");
        CATEGORY_SYNONYMS.put("motorcycle", "Bike");
        CATEGORY_SYNONYMS.put("two wheeler", "Bike");
        CATEGORY_SYNONYMS.put("two-wheeler", "Bike");
        CATEGORY_SYNONYMS.put("scooter", "Bike");
        CATEGORY_SYNONYMS.put("property", "Home");
        CATEGORY_SYNONYMS.put("house insurance", "Home");
        CATEGORY_SYNONYMS.put("home insurance", "Home");
        CATEGORY_SYNONYMS.put("medical insurance", "Health");
        CATEGORY_SYNONYMS.put("medical", "Health");
        CATEGORY_SYNONYMS.put("mediclaim", "Health");
        CATEGORY_SYNONYMS.put("term plan", "Life");
        CATEGORY_SYNONYMS.put("term insurance", "Life");
        CATEGORY_SYNONYMS.put("travel insurance", "Travel");
        CATEGORY_SYNONYMS.put("trip insurance", "Travel");
        CATEGORY_SYNONYMS.put("lorry", "Truck");
        CATEGORY_SYNONYMS.put("heavy vehicle", "Truck");
        CATEGORY_SYNONYMS.put("commercial vehicle", "Truck");
    }

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqUrl;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AgentRepository agentRepository;

    private String cachedSchema = "";

    public enum Intent {
        PORTFOLIO, RENEWALS, REVENUE, CLIENTS, MESSAGES, PERFORMANCE, GENERAL
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Startup: build rich schema from DB (Level 6)
    // ─────────────────────────────────────────────────────────────────────────

    @PostConstruct
    public void initSchema() {
        try {
            cachedSchema = buildSchemaFromDb();
            logger.info("[NLQuery] Schema loaded from database.");
        } catch (Exception e) {
            logger.warn("[NLQuery] DB schema load failed, using fallback: {}", e.getMessage());
            cachedSchema = getFallbackSchema();
        }
    }

    private String buildSchemaFromDb() {
        String sql = "SELECT c.table_name, c.column_name, c.data_type, c.is_nullable, " +
                "  CASE WHEN pk.column_name IS NOT NULL THEN 'YES' ELSE 'NO' END AS is_pk, " +
                "  CASE WHEN fk.column_name IS NOT NULL THEN 'YES' ELSE 'NO' END AS is_fk, " +
                "  fk.foreign_table_name, fk.foreign_column_name " +
                "FROM information_schema.columns c " +
                "LEFT JOIN ( " +
                "  SELECT ku.table_name, ku.column_name " +
                "  FROM information_schema.table_constraints tc " +
                "  JOIN information_schema.key_column_usage ku ON tc.constraint_name = ku.constraint_name " +
                "  WHERE tc.constraint_type = 'PRIMARY KEY' AND tc.table_schema = 'public' " +
                ") pk ON pk.table_name = c.table_name AND pk.column_name = c.column_name " +
                "LEFT JOIN ( " +
                "  SELECT kcu.table_name, kcu.column_name, " +
                "         ccu.table_name AS foreign_table_name, ccu.column_name AS foreign_column_name " +
                "  FROM information_schema.table_constraints tc " +
                "  JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name " +
                "  JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name " +
                "  WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public' " +
                ") fk ON fk.table_name = c.table_name AND fk.column_name = c.column_name " +
                "WHERE c.table_schema = 'public' " +
                "  AND c.table_name IN ('agents','clients','policies','message_logs') " +
                "ORDER BY c.table_name, c.ordinal_position";

        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        Map<String, List<String>> tableColumns = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String table = (String) row.get("table_name");
            String col = (String) row.get("column_name");
            String type = (String) row.get("data_type");
            String isPk = (String) row.get("is_pk");
            String isFk = (String) row.get("is_fk");
            String fTable = (String) row.get("foreign_table_name");
            String fCol = (String) row.get("foreign_column_name");

            StringBuilder desc = new StringBuilder(col).append(" (").append(type);
            if ("YES".equals(isPk))
                desc.append(", PK");
            if ("YES".equals(isFk))
                desc.append(", FK -> ").append(fTable).append(".").append(fCol);
            if ("NO".equals(row.get("is_nullable")))
                desc.append(", NOT NULL");
            desc.append(")");
            tableColumns.computeIfAbsent(table, k -> new ArrayList<>()).add(desc.toString());
        }

        // Level 6 — schema with business meaning
        StringBuilder schema = new StringBuilder("=== DATABASE SCHEMA ===\n\n");

        schema.append("TABLE: agents — One row per insurance agent (the logged-in user)\n");
        tableColumns.getOrDefault("agents", List.of()).forEach(c -> schema.append("  ").append(c).append("\n"));
        schema.append("\n");

        schema.append("TABLE: clients — Insurance policy holders managed by an agent\n");
        tableColumns.getOrDefault("clients", List.of()).forEach(c -> schema.append("  ").append(c).append("\n"));
        schema.append("\n");

        schema.append("TABLE: policies — Insurance policies. Each belongs to one client.\n");
        schema.append("  vehicle_type = insurance category (Auto, Home, Bike, SUV, Truck, Health, Life, Travel)\n");
        schema.append("  status = ACTIVE (valid) | EXPIRED (lapsed) | RENEWED (renewed for new term)\n");
        schema.append("  renewal_status = PENDING | AUTO_RENEWED | MANUAL_RENEWED | RENEWED\n");
        schema.append("  premium = annual premium in Indian Rupees (INR)\n");
        schema.append("  expiry_date = when policy coverage ends\n");
        tableColumns.getOrDefault("policies", List.of()).forEach(c -> schema.append("  ").append(c).append("\n"));
        schema.append("\n");

        schema.append("TABLE: message_logs — Every SMS/WhatsApp renewal reminder sent\n");
        schema.append("  reminder_type = SEVEN_DAYS | THREE_DAYS | EXPIRY_DAY\n");
        schema.append("  channel = SMS | WHATSAPP\n");
        schema.append("  status = SENT (delivered) | FAILED (not delivered) | PENDING\n");
        schema.append("  retry_count = number of retries attempted (max 3)\n");
        tableColumns.getOrDefault("message_logs", List.of()).forEach(c -> schema.append("  ").append(c).append("\n"));
        schema.append("\n");

        // Level 5 synonyms baked into schema context
        schema.append("=== CATEGORY SYNONYMS (user term → vehicle_type in DB) ===\n");
        schema.append("  car / automobile / four wheeler → 'Auto'\n");
        schema.append("  bike / motorbike / two wheeler / scooter → 'Bike'\n");
        schema.append("  home / house / property → 'Home'\n");
        schema.append("  health / medical / mediclaim → 'Health'\n");
        schema.append("  life / term / term plan → 'Life'\n");
        schema.append("  travel / trip → 'Travel'\n");
        schema.append("  truck / lorry / commercial → 'Truck'\n");
        schema.append("  suv → 'SUV'\n\n");

        schema.append("=== MANDATORY RULES ===\n");
        schema.append("- EVERY query: JOIN clients c ON p.client_id = c.id WHERE c.agent_id = <id>\n");
        schema.append("- Category filters: use ILIKE not = (e.g. p.vehicle_type ILIKE 'Auto')\n");
        schema.append("- Multi-category: p.vehicle_type ILIKE ANY(ARRAY['%Auto%','%Car%'])\n");
        schema.append("- Always COALESCE nullable columns in SELECT\n");
        schema.append("- Always alias aggregates: COUNT(*) AS policy_count, SUM(premium) AS total_premium\n");

        return schema.toString();
    }

    private String getFallbackSchema() {
        return "TABLE: agents — id(PK), username, email, full_name\n" +
                "TABLE: clients — id(PK), full_name, email, phone_number, whatsapp_number, agent_id(FK)\n" +
                "TABLE: policies — id(PK), vehicle_type[Auto|Home|Bike|SUV|Truck|Health|Life|Travel],\n" +
                "                  expiry_date, premium(INR), status[ACTIVE|EXPIRED|RENEWED],\n" +
                "                  renewal_status[PENDING|AUTO_RENEWED|MANUAL_RENEWED|RENEWED], client_id(FK)\n" +
                "TABLE: message_logs — id(PK), policy_id(FK), channel[SMS|WHATSAPP],\n" +
                "                      status[SENT|FAILED|PENDING], retry_count, sent_at\n" +
                "SYNONYMS: car→Auto, bike→Bike, house→Home, medical→Health, term→Life\n" +
                "RULE: always JOIN clients c ON p.client_id = c.id WHERE c.agent_id = <agentId>\n";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    public AskResult ask(String question, String username,
            List<Map<String, String>> history,
            SessionMemory sessionMemory) {

        if (question == null || question.trim().isEmpty()) {
            return new AskResult(
                    "Please ask a question about your policies, clients, revenue, or messages.",
                    null, null, sessionMemory);
        }

        Agent agent = agentRepository.findByUsername(username).orElse(null);
        if (agent == null) {
            return new AskResult("Error: Agent not found for username: " + username, null, null, sessionMemory);
        }
        Long agentId = agent.getId();

        // Level 5 — Apply synonym mapping first
        String mappedQuestion = applySynonymMapping(question);
        logger.info("[NLQuery] Synonym mapped: '{}' → '{}'", question, mappedQuestion);

        // Level 4 — Detect if question references previous result
        boolean isReferencing = detectsPreviousReference(mappedQuestion);

        // Classify intent on original question BEFORE follow-up expansion
        // so "give me only Auto" inherits PORTFOLIO from history
        Intent intent = classifyIntent(mappedQuestion, history);
        logger.info("[NLQuery] Intent={} | ReferencingPrevious={}", intent, isReferencing);

        // Level 7 — Summarize history if too long
        List<Map<String, String>> effectiveHistory = maybeSummarizeHistory(history);

        // Generate SQL and Execute with Self-Correction loop
        String sql = null;
        List<Map<String, Object>> rows = null;
        String rawResult = null;
        String lastError = null;

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                // Generate SQL (passing error context if this is a retry)
                sql = generateSql(mappedQuestion, agentId, intent, effectiveHistory, sessionMemory, isReferencing, sql, lastError);
                if (attempt > 1) {
                    logger.info("[NLQuery] Retry Attempt {}: New SQL generated", attempt);
                }
                logger.info("[NLQuery] Testing SQL:\n{}", sql);

                // Execute
                rows = jdbc.queryForList(sql);
                rawResult = new ObjectMapper().writeValueAsString(rows);
                logger.info("[NLQuery] SQL success on attempt {}", attempt);
                break; // Success! Exit loop
            } catch (Exception e) {
                lastError = e.getMessage();
                logger.warn("[NLQuery] Attempt {} failed: {}", attempt, lastError);
                if (attempt == 2) {
                    // Exhausted retries
                    return new AskResult("I had trouble fetching that data even after correction. Please try rephrasing your question.", sql, null, sessionMemory);
                }
            }
        }

        // Level 3 — Update session memory
        SessionMemory newMemory = extractNewSessionMemory(mappedQuestion, rawResult, sql, intent, sessionMemory);

        // Format answer
        try {
            String answer = formatAnswer(mappedQuestion, rawResult, intent, effectiveHistory, newMemory);
            return new AskResult(answer, sql, rows, newMemory);
        } catch (Exception e) {
            logger.error("[NLQuery] Formatting failed: {}", e.getMessage());
            return new AskResult("I collected the data but had trouble formatting the summary.", sql, rows, newMemory);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Level 5 — Synonym Mapping
    // ─────────────────────────────────────────────────────────────────────────

    private String applySynonymMapping(String question) {
        if (question == null)
            return "";
        String result = question;
        for (Map.Entry<String, String> entry : CATEGORY_SYNONYMS.entrySet()) {
            result = result.replaceAll("(?i)\\b" + entry.getKey() + "\\b", entry.getValue());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Level 4 — Previous Reference Detection
    // ─────────────────────────────────────────────────────────────────────────

    private boolean detectsPreviousReference(String question) {
        String q = " " + question.toLowerCase() + " ";
        return anyMatch(q,
                " this ", " those ", " them ", " these ", " that ",
                "of this", "of those", "from this", "from those",
                "among these", "in this list", "give me only",
                "filter by", "break it down", "drill down",
                "sort that", "order that", "show me more",
                "same list", "above list", "those clients",
                "those policies", "that data");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Level 7 — History Summarization
    // ─────────────────────────────────────────────────────────────────────────

    private List<Map<String, String>> maybeSummarizeHistory(List<Map<String, String>> history) {
        if (history == null || history.size() <= 16)
            return history;

        int splitPoint = history.size() / 2;
        List<Map<String, String>> oldMessages = history.subList(0, splitPoint);
        List<Map<String, String>> recentMessages = history.subList(splitPoint, history.size());

        try {
            StringBuilder convText = new StringBuilder();
            for (Map<String, String> turn : oldMessages) {
                convText.append(turn.get("role").toUpperCase()).append(": ")
                        .append(turn.get("content")).append("\n");
            }

            String summary = callGroq(
                    "Summarize this insurance business conversation in 3-4 sentences. " +
                            "Focus on topics discussed, data retrieved, and what the user is trying to do. " +
                            "Be specific about categories, time periods, and numbers. Output ONLY the summary.",
                    convText.toString()).strip();

            logger.info("[NLQuery] Summarized {} messages → {}", oldMessages.size(), summary);

            List<Map<String, String>> compressed = new ArrayList<>();
            compressed.add(Map.of("role", "system", "content",
                    "[Earlier conversation summary]: " + summary));
            compressed.addAll(recentMessages);
            return compressed;

        } catch (Exception e) {
            logger.warn("[NLQuery] Summarization failed, trimming to last 8 exchanges: {}", e.getMessage());
            return history.subList(Math.max(0, history.size() - 16), history.size());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Level 3 — Session Memory Extraction
    // ─────────────────────────────────────────────────────────────────────────

    private SessionMemory extractNewSessionMemory(String question, String rawResult,
            String sql, Intent intent,
            SessionMemory previous) {
        String topic = switch (intent) {
            case PORTFOLIO -> "portfolio";
            case RENEWALS -> "renewals";
            case REVENUE -> "revenue";
            case CLIENTS -> "clients";
            case MESSAGES -> "messages";
            case PERFORMANCE -> "performance";
            default -> previous != null ? previous.lastTopic() : "general";
        };

        // Detect categories in the SQL
        List<String> categories = new ArrayList<>();
        if (sql != null) {
            String sqlLower = sql.toLowerCase();
            for (String cat : List.of("Auto", "Home", "Bike", "SUV", "Truck", "Health", "Life", "Travel")) {
                if (sqlLower.contains(cat.toLowerCase()))
                    categories.add(cat);
            }
        }
        if (categories.isEmpty() && previous != null && previous.lastCategories() != null) {
            categories = previous.lastCategories();
        }

        String resultSummary = (rawResult == null || rawResult.equals("[]"))
                ? "No data found"
                : rawResult.length() > 400 ? rawResult.substring(0, 400) + "..." : rawResult;

        return new SessionMemory(topic, categories, resultSummary, sql);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Intent Classification
    // ─────────────────────────────────────────────────────────────────────────

    Intent classifyIntent(String question, List<Map<String, String>> history) {
        Intent current = classifyIntent(question);
        if (current != Intent.GENERAL)
            return current;

        // Short follow-up — inherit from history
        if (question.trim().length() < 40 && history != null && history.size() >= 2) {
            for (int i = history.size() - 1; i >= 0; i--) {
                Map<String, String> turn = history.get(i);
                if ("user".equals(turn.get("role"))) {
                    Intent inherited = classifyIntent(turn.get("content"));
                    if (inherited != Intent.GENERAL) {
                        logger.info("[NLQuery] Inherited intent {} from history", inherited);
                        return inherited;
                    }
                }
            }
        }
        return Intent.GENERAL;
    }

    Intent classifyIntent(String question) {
        String q = question.toLowerCase();

        if (anyMatch(q, "portfolio", "mix", "breakdown", "distribution", "category",
                "types of policy", "vehicle type", "split", "composition",
                "auto", "home", "bike", "suv", "truck", "health", "life", "travel",
                "insurer", "what types")) {
            return Intent.PORTFOLIO;
        }
        if (anyMatch(q, "revenue", "income", "earning", "money", "premium", "total premium",
                "how much", "sum", "amount", "rupee", "payment", "collected", "commission", "earn")) {
            return Intent.REVENUE;
        }
        if (anyMatch(q, "renew", "renewal", "expir", "due", "upcoming", "pending",
                "lapse", "lapsed", "overdue", "deadline", "soon",
                "this month", "this week", "next week", "next month")) {
            return Intent.RENEWALS;
        }
        if (anyMatch(q, "client", "customer", "who", "person", "people", "top client",
                "best client", "lost client", "contact", "name", "email", "phone")) {
            return Intent.CLIENTS;
        }
        if (anyMatch(q, "message", "sms", "whatsapp", "reminder", "notification",
                "sent", "failed message", "communication", "delivery", "retry")) {
            return Intent.MESSAGES;
        }
        if (anyMatch(q, "rate", "conversion", "success rate", "performance",
                "kpi", "metric", "percentage", "how well", "efficiency")) {
            return Intent.PERFORMANCE;
        }
        return Intent.GENERAL;
    }

    private boolean anyMatch(String q, String... keywords) {
        for (String kw : keywords)
            if (q.contains(kw))
                return true;
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SQL Generation
    // ─────────────────────────────────────────────────────────────────────────

    private String generateSql(String question, Long agentId, Intent intent,
            List<Map<String, String>> history,
            SessionMemory sessionMemory, boolean isReferencing, String failedSql, String error) {
        String systemPrompt = buildSqlSystemPrompt(intent);
        String userMessage = buildSqlUserMessage(question, agentId, sessionMemory, isReferencing, failedSql, error);
        // Level 1 — full history to Groq
        List<Map<String, Object>> messages = buildGroqMessages(systemPrompt, history, userMessage);
        return cleanSql(callGroq(messages));
    }

    /**
     * Level 1 — Build full message list: [system] + [history] + [current user msg]
     */
    private List<Map<String, Object>> buildGroqMessages(String systemPrompt,
            List<Map<String, String>> history,
            String currentUserMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        if (history != null) {
            for (Map<String, String> turn : history) {
                String role = turn.get("role");
                String content = turn.get("content");
                if (role == null || content == null || content.isBlank() || "system".equals(role))
                    continue;
                String trimmed = content.length() > 500 ? content.substring(0, 500) + "..." : content;
                messages.add(Map.of("role", role, "content", trimmed));
            }
        }

        messages.add(Map.of("role", "user", "content", currentUserMessage));
        return messages;
    }

    /**
     * Level 2 — Comprehensive system prompt with business rules + Level 6 schema
     */
    private String buildSqlSystemPrompt(Intent intent) {
        return "You are an expert PostgreSQL analyst for an Indian insurance business.\n" +
                "Output ONLY a single valid SELECT query — no markdown, no backticks, no explanation.\n\n" +

                "=== BEHAVIOR RULES ===\n" +
                "1. Understand follow-up questions using conversation history (full history is provided).\n" +
                "2. 'this', 'those', 'them', 'these' = previous query result — modify that SQL, not a new one.\n" +
                "3. If 'Previous SQL' is in the user message, MODIFY it for follow-up questions.\n" +
                "4. 'give me only Auto' on a portfolio list → add WHERE p.vehicle_type ILIKE 'Auto'.\n" +
                "5. SELECT only 4-8 relevant columns. NEVER SELECT *.\n" +
                "6. Always filter by agent_id (value in user message).\n" +
                "7. Use ILIKE for text searches. Use COALESCE for nullable columns.\n" +
                "8. End with a semicolon. NEVER use INSERT/UPDATE/DELETE/DROP/ALTER.\n\n" +

                "=== INSURANCE BUSINESS CONTEXT ===\n" +
                "- premium = annual fee in Indian Rupees (INR)\n" +
                "- ACTIVE = policy is valid; EXPIRED = lapsed; RENEWED = renewed for another year\n" +
                "- A 'lost client' = status='EXPIRED' AND renewal_status='PENDING'\n" +
                "- expiry_date = when coverage ends — always sort by this for renewal queries\n\n" +

                cachedSchema + "\n\n" +
                getIntentExamples(intent);
    }

    private String getIntentExamples(Intent intent) {
        return switch (intent) {
            case PORTFOLIO ->
                "=== PORTFOLIO EXAMPLES ===\n" +
                        "Q: portfolio mix\n" +
                        "A: SELECT COALESCE(p.vehicle_type,'Other') AS category, COUNT(p.id) AS policy_count, SUM(p.premium) AS total_premium\n"
                        +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND p.status = 'ACTIVE'\n" +
                        "   GROUP BY category ORDER BY total_premium DESC;\n\n" +
                        "Q: give me only Auto (follow-up)\n" +
                        "A: SELECT c.full_name, p.policy_number, p.vehicle_type, p.premium, p.expiry_date, p.status\n" +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND p.vehicle_type ILIKE 'Auto'\n" +
                        "   ORDER BY p.premium DESC;\n";

            case REVENUE ->
                "=== REVENUE EXAMPLES ===\n" +
                        "Q: total revenue this month\n" +
                        "A: SELECT SUM(p.premium) AS total_revenue FROM policies p JOIN clients c ON p.client_id = c.id\n"
                        +
                        "   WHERE c.agent_id = :agentId AND p.status = 'ACTIVE'\n" +
                        "     AND DATE_TRUNC('month', p.created_at) = DATE_TRUNC('month', CURRENT_DATE);\n\n" +
                        "Q: monthly revenue breakdown this year\n" +
                        "A: SELECT TO_CHAR(p.created_at,'Mon') AS month, EXTRACT(MONTH FROM p.created_at) AS month_num,\n"
                        +
                        "          SUM(p.premium) AS total_revenue\n" +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)\n"
                        +
                        "   GROUP BY month, month_num ORDER BY month_num;\n";

            case RENEWALS ->
                "=== RENEWAL EXAMPLES ===\n" +
                        "Q: policies expiring this month\n" +
                        "A: SELECT c.full_name, p.policy_number, p.vehicle_type, p.expiry_date, p.premium, p.renewal_status\n"
                        +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND p.status = 'ACTIVE'\n" +
                        "     AND p.expiry_date BETWEEN CURRENT_DATE AND (DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')\n"
                        +
                        "   ORDER BY p.expiry_date ASC;\n\n" +
                        "Q: how much of this is car and bike (follow-up)\n" +
                        "A: SELECT COALESCE(p.vehicle_type,'Other') AS category, COUNT(p.id) AS policy_count, SUM(p.premium) AS total_premium\n"
                        +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND p.status = 'ACTIVE'\n" +
                        "     AND p.expiry_date BETWEEN CURRENT_DATE AND (DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')\n"
                        +
                        "     AND p.vehicle_type ILIKE ANY(ARRAY['%Auto%','%Car%','%Bike%'])\n" +
                        "   GROUP BY category;\n\n" +
                        "Q: lost clients\n" +
                        "A: SELECT c.full_name, c.phone_number, p.policy_number, p.vehicle_type, p.expiry_date, p.premium\n"
                        +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND p.status = 'EXPIRED' AND p.renewal_status = 'PENDING'\n" +
                        "   ORDER BY p.expiry_date DESC;\n";

            case CLIENTS ->
                "=== CLIENT EXAMPLES ===\n" +
                        "Q: top 5 clients by premium\n" +
                        "A: SELECT c.full_name, c.email, c.phone_number, COUNT(p.id) AS policy_count, SUM(p.premium) AS total_premium\n"
                        +
                        "   FROM clients c JOIN policies p ON c.id = p.client_id\n" +
                        "   WHERE c.agent_id = :agentId GROUP BY c.id, c.full_name, c.email, c.phone_number\n" +
                        "   ORDER BY total_premium DESC LIMIT 5;\n\n" +
                        "Q: clients with no whatsapp\n" +
                        "A: SELECT c.full_name, c.email, c.phone_number, COUNT(p.id) AS policy_count\n" +
                        "   FROM clients c LEFT JOIN policies p ON c.id = p.client_id\n" +
                        "   WHERE c.agent_id = :agentId AND (c.whatsapp_number IS NULL OR c.whatsapp_number = '')\n" +
                        "   GROUP BY c.id, c.full_name, c.email, c.phone_number ORDER BY policy_count DESC;\n";

            case MESSAGES ->
                "=== MESSAGE EXAMPLES ===\n" +
                        "Q: failed messages\n" +
                        "A: SELECT ml.channel, COUNT(ml.id) AS failed_count\n" +
                        "   FROM message_logs ml JOIN policies p ON ml.policy_id = p.id JOIN clients c ON p.client_id = c.id\n"
                        +
                        "   WHERE c.agent_id = :agentId AND ml.status = 'FAILED' GROUP BY ml.channel;\n\n" +
                        "Q: whatsapp success rate\n" +
                        "A: SELECT COUNT(*) AS total,\n" +
                        "          SUM(CASE WHEN ml.status = 'SENT' THEN 1 ELSE 0 END) AS successful,\n" +
                        "          ROUND(100.0 * SUM(CASE WHEN ml.status = 'SENT' THEN 1 ELSE 0 END) / NULLIF(COUNT(*),0), 2) AS success_rate_pct\n"
                        +
                        "   FROM message_logs ml JOIN policies p ON ml.policy_id = p.id JOIN clients c ON p.client_id = c.id\n"
                        +
                        "   WHERE c.agent_id = :agentId AND ml.channel = 'WHATSAPP';\n";

            case PERFORMANCE ->
                "=== PERFORMANCE EXAMPLES ===\n" +
                        "Q: renewal conversion rate\n" +
                        "A: SELECT COUNT(*) AS total_expiring,\n" +
                        "       SUM(CASE WHEN p.renewal_status IN ('AUTO_RENEWED','MANUAL_RENEWED','RENEWED') THEN 1 ELSE 0 END) AS renewed,\n"
                        +
                        "       ROUND(100.0 * SUM(CASE WHEN p.renewal_status IN ('AUTO_RENEWED','MANUAL_RENEWED','RENEWED') THEN 1 ELSE 0 END) / NULLIF(COUNT(*),0), 2) AS conversion_rate_pct\n"
                        +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND p.expiry_date BETWEEN CURRENT_DATE - INTERVAL '30 days' AND CURRENT_DATE;\n";

            default ->
                "=== GENERAL EXAMPLES ===\n" +
                        "Q: full summary\n" +
                        "A: SELECT COUNT(DISTINCT c.id) AS total_clients, COUNT(p.id) AS total_policies,\n" +
                        "       SUM(CASE WHEN p.status='ACTIVE' THEN 1 ELSE 0 END) AS active,\n" +
                        "       SUM(CASE WHEN p.status='EXPIRED' THEN 1 ELSE 0 END) AS expired,\n" +
                        "       SUM(CASE WHEN p.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days' THEN 1 ELSE 0 END) AS expiring_soon,\n"
                        +
                        "       COALESCE(SUM(CASE WHEN p.status='ACTIVE' THEN p.premium END), 0) AS active_premium_inr\n"
                        +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id WHERE c.agent_id = :agentId;\n";
        };
    }

    /**
     * Level 3+4 — Build SQL user message with session memory context injected
     */
    private String buildSqlUserMessage(String question, Long agentId,
            SessionMemory sessionMemory, boolean isReferencing, String failedSql, String error) {
        StringBuilder msg = new StringBuilder();

        if (failedSql != null && error != null) {
            msg.append("=== !!! ATTENTION: PREVIOUS SQL FAILED !!! ===\n")
               .append("The following SQL was generated previously but threw an error:\n")
               .append(failedSql).append("\n\n")
               .append("ERROR MESSAGE: ").append(error).append("\n")
               .append("Please analyze the error and provide a CORRECTED SQL query that fixes the issue.\n\n");
        }

        if (sessionMemory != null) {
            if (sessionMemory.lastSql() != null && !sessionMemory.lastSql().isBlank() && failedSql == null) {
                msg.append("=== PREVIOUS SQL (modify this for follow-up questions) ===\n")
                        .append(sessionMemory.lastSql()).append("\n\n");
            }
            if (isReferencing && sessionMemory.lastResultSummary() != null) {
                msg.append("=== PREVIOUS RESULT (what 'this'/'those' refers to) ===\n")
                        .append(sessionMemory.lastResultSummary()).append("\n\n");
            }
            if (sessionMemory.lastTopic() != null) {
                msg.append("Current topic: ").append(sessionMemory.lastTopic()).append("\n");
            }
            if (sessionMemory.lastCategories() != null && !sessionMemory.lastCategories().isEmpty()) {
                msg.append("Categories in focus: ")
                        .append(String.join(", ", sessionMemory.lastCategories())).append("\n");
            }
        }

        if (isReferencing && failedSql == null) {
            msg.append("\nIMPORTANT: User references previous data. Modify Previous SQL if available.\n");
        }

        msg.append("\nQuestion: ").append(question).append("\n")
                .append("Agent ID: ").append(agentId).append("\n")
                .append("SQL:");

        return msg.toString();
    }

    private String cleanSql(String raw) {
        if (raw == null)
            throw new RuntimeException("Groq returned null SQL");
        String cleaned = raw.strip()
                .replaceAll("(?i)```sql\\s*", "").replaceAll("```", "").strip();
        int idx = cleaned.toUpperCase().indexOf("SELECT");
        if (idx > 0)
            cleaned = cleaned.substring(idx);
        int semi = cleaned.indexOf(";");
        if (semi != -1)
            cleaned = cleaned.substring(0, semi + 1);
        return cleaned.strip();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Execute SQL (safety-checked)
    // ─────────────────────────────────────────────────────────────────────────

    private String executeQuery(String sql, Long agentId) {
        String upper = sql.trim().toUpperCase();
        if (!upper.startsWith("SELECT"))
            throw new IllegalArgumentException(
                    "Only SELECT allowed. Got: " + sql.substring(0, Math.min(80, sql.length())));

        // Use word-boundary matching to avoid false positives
        // (e.g. "created_at" should NOT match "CREATE", "EXTRACT" should NOT match
        // "EXEC")
        for (String kw : new String[] { "INSERT", "UPDATE", "DELETE", "DROP", "TRUNCATE", "ALTER",
                "CREATE TABLE", "CREATE INDEX", "CREATE VIEW", "GRANT", "EXECUTE" })
            if (java.util.regex.Pattern.compile("\\b" + kw + "\\b").matcher(upper).find())
                throw new IllegalArgumentException("Blocked keyword: " + kw);

        if (!sql.contains(agentId.toString()))
            throw new IllegalArgumentException("Query must be scoped to agent ID " + agentId);

        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        if (rows.isEmpty())
            return "[]";

        try {
            return new ObjectMapper().writeValueAsString(rows);
        } catch (Exception e) {
            logger.error("[NLQuery] JSON serialization failed: {}", e.getMessage());
            return rows.toString(); // Fallback
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Format Answer (Level 1: full history + Level 2: smart system prompt)
    // ─────────────────────────────────────────────────────────────────────────

    private String formatAnswer(String question, String rawResult, Intent intent,
            List<Map<String, String>> history, SessionMemory newMemory) {

        // Level 2 — business-aware formatter system prompt
        String systemPrompt = "You are a friendly insurance business analyst assistant.\n" +
                "Convert raw database results into clear, professional, business-focused answers.\n\n" +
                "=== FORMATTING RULES ===\n" +
                "- Indian Rupees format: Rs. 1,23,456 (use Indian number system: lakhs not millions)\n" +
                "- Round percentages to 1 decimal place\n" +
                "- If data contains multiple rows, return:\n" +
                "  1. A brief, professional summary of the findings.\n" +
                "  2. Important insights or patterns observed (e.g. 'Most expiring policies are for cars').\n" +
                "  3. Relevant totals or averages at the bottom.\n" +
                "- DO NOT generate Markdown Tables. Focus on text and summaries only.\n" +
                "- If data is a single value or count: return a clear 1-2 sentence response.\n" +
                "- NEVER say 'No data found' unless result is literally [] with zero rows\n" +
                "- NEVER mention SQL, database, columns, or technical terms\n" +
                "- Use business language: 'policies', 'clients', 'premiums', 'renewals'\n\n" +
                "=== CONVERSATION RULES ===\n" +
                "- Full conversation history is provided — continue naturally from previous answers\n" +
                "- If this is a follow-up filter, confirm what was filtered (e.g. 'Showing only Auto policies:')\n" +
                "- Do NOT repeat information already given in the conversation\n\n" +
                "Context: " + switch (intent) {
                    case PORTFOLIO -> "User is asking about insurance portfolio distribution.";
                    case REVENUE -> "User is asking about money and premiums in INR.";
                    case RENEWALS -> "User is asking about policy renewals and expiry.";
                    case CLIENTS -> "User is asking about their insurance clients.";
                    case MESSAGES -> "User is asking about SMS/WhatsApp reminders.";
                    case PERFORMANCE -> "User is asking about business KPIs.";
                    default -> "User is asking a general insurance question.";
                };

        String memCtx = (newMemory != null && newMemory.lastTopic() != null)
                ? "Topic: " + newMemory.lastTopic() + "\n"
                : "";

        String userMessage = memCtx + "\n"
                + "Question: \"" + question + "\"\n"
                + "Data: " + rawResult + "\n\n"
                + "Format the response professionally. provide a brief summary of findings and any key business insights. DO NOT generate a table.";

        // Level 1 — full history to formatter too
        List<Map<String, Object>> messages = buildGroqMessages(systemPrompt, history, userMessage);
        String answer = callGroq(messages).strip();
        logger.info("[NLQuery] Formatted Answer (first 100 chars): {}", 
                    answer.length() > 100 ? answer.substring(0, 100) + "..." : answer);
        return answer;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Groq API Client
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Convenience overload for simple calls (summarization, follow-up expansion)
     */
    private String callGroq(String systemPrompt, String userMessage) {
        return callGroq(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)));
    }

    /**
     * Level 1 — Main call, accepts full message list for proper multi-turn context
     */
    @SuppressWarnings("unchecked")
    private String callGroq(List<Map<String, Object>> messages) {
        if (groqApiKey == null || groqApiKey.trim().isEmpty())
            throw new RuntimeException("Groq API key not configured.");

        try {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(10_000);
            factory.setReadTimeout(30_000);
            RestTemplate rest = new RestTemplate(factory);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", groqModel);
            body.put("temperature", 0);
            body.put("max_tokens", 1024);
            body.put("messages", messages);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + groqApiKey);
            headers.set("Content-Type", "application/json");

            Map<?, ?> response = rest.postForObject(
                    groqUrl,
                    new org.springframework.http.HttpEntity<>(body, headers),
                    Map.class);

            if (response != null && response.containsKey("choices")) {
                List<?> choices = (List<?>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                    Map<?, ?> message = (Map<?, ?>) choice.get("message");
                    return (String) message.get("content");
                }
            }
            throw new RuntimeException("Empty response from Groq.");

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            logger.error("[NLQuery] Groq HTTP error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Groq API error: " + e.getStatusCode());
        } catch (Exception e) {
            logger.error("[NLQuery] Groq call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to reach Groq: " + e.getMessage());
        }
    }
}