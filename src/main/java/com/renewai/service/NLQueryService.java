package com.renewai.service;

import com.renewai.entity.Agent;
import com.renewai.repository.AgentRepository;
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
 * Natural Language Query Service — Intent-Routed Architecture
 *
 * Flow:
 * 1. Java classifies the question into an Intent (no LLM call needed)
 * 2. Intent-specific system prompt + rich examples sent to Groq → SQL
 * 3. SQL executed against PostgreSQL (agent-scoped, safety-checked)
 * 4. Raw DB result sent to Groq → friendly structured answer
 *
 * To add a new query type:
 * - Add keywords to classifyIntent()
 * - Add a new case in getIntentExamples()
 */
@Service
public class NLQueryService {

    private static final Logger logger = LoggerFactory.getLogger(NLQueryService.class);

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

    // ── Intent categories ─────────────────────────────────────────────────────
    public enum Intent {
        PORTFOLIO, // policy mix, breakdown, distribution by type
        RENEWALS, // expiring, renewal rate, pending, upcoming
        REVENUE, // premium, income, total revenue, earnings
        CLIENTS, // client info, top clients, lost clients
        MESSAGES, // sms, whatsapp, failed messages, reminders
        PERFORMANCE, // conversion rate, success rate, KPIs
        GENERAL // fallback
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Startup: load real schema from DB
    // ─────────────────────────────────────────────────────────────────────────

    @PostConstruct
    public void initSchema() {
        try {
            cachedSchema = buildSchemaFromDb();
            logger.info("[NLQuery] Schema loaded successfully from database.");
        } catch (Exception e) {
            logger.warn("[NLQuery] DB schema load failed, using fallback. Error: {}", e.getMessage());
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

        StringBuilder schema = new StringBuilder("Database Schema (PostgreSQL):\n\n");
        for (Map.Entry<String, List<String>> entry : tableColumns.entrySet()) {
            schema.append("TABLE: ").append(entry.getKey()).append("\n");
            entry.getValue().forEach(c -> schema.append("  - ").append(c).append("\n"));
            schema.append("\n");
        }

        schema.append("Business rules:\n")
                .append("- status: ACTIVE | EXPIRED | RENEWED\n")
                .append("- renewal_status: PENDING | AUTO_RENEWED | MANUAL_RENEWED | RENEWED\n")
                .append("- channel: SMS | WHATSAPP\n")
                .append("- reminder_type: SEVEN_DAYS | THREE_DAYS | EXPIRY_DAY\n")
                .append("- message status: SENT | FAILED | PENDING\n")
                .append("- premium is stored in Indian Rupees (INR)\n")
                .append("- EVERY query MUST join through clients: JOIN clients c ON p.client_id = c.id WHERE c.agent_id = <id>\n");

        return schema.toString();
    }

    private String getFallbackSchema() {
        return "TABLE: agents        - id(PK), username, email, full_name, phone_number, active\n" +
                "TABLE: clients       - id(PK), full_name, email, phone_number, whatsapp_number, address, agent_id(FK->agents)\n"
                +
                "TABLE: policies      - id(PK), policy_number, policy_type, vehicle_type, registration_number,\n" +
                "                       insurer_name, start_date, expiry_date, premium(INR), premium_frequency,\n" +
                "                       status(ACTIVE|EXPIRED|RENEWED), renewal_status(PENDING|AUTO_RENEWED|MANUAL_RENEWED|RENEWED),\n"
                +
                "                       manual_renewal_notes, client_id(FK->clients)\n" +
                "TABLE: message_logs  - id(PK), policy_id(FK->policies), customer_id(FK->clients),\n" +
                "                       reminder_type(SEVEN_DAYS|THREE_DAYS|EXPIRY_DAY),\n" +
                "                       channel(SMS|WHATSAPP), status(SENT|FAILED|PENDING),\n" +
                "                       retry_count, failure_reason, sent_at\n" +
                "Rule: always JOIN clients c ON p.client_id = c.id WHERE c.agent_id = <agentId>\n";
    }

    /// resolve follow up
    ///
    /**
     * Detects follow-up questions and rewrites them with full context.
     * e.g. "show me those clients" -> "show me the clients with failed whatsapp
     * messages"
     * e.g. "now filter by this month" -> "policies expiring in next 30 days, filter
     * by this month"
     */
    private String resolveFollowUp(String question, List<Map<String, String>> history) {
        if (history == null || history.isEmpty())
            return question;

        String q = question.toLowerCase().trim();

        // Detect if this is a follow-up (contains references or is very short)
        boolean isFollowUp = q.length() < 25
                || anyMatch(q, "those", "them", "their", "these", "that", "the same",
                        "more details", "show more", "also", "and also", "what about",
                        "now filter", "filter by", "sort by", "order by",
                        "break it down", "drill down", "zoom in");

        if (!isFollowUp)
            return question;

        // Build context from last 2 exchanges
        StringBuilder context = new StringBuilder();
        int start = Math.max(0, history.size() - 4);
        for (Map<String, String> turn : history.subList(start, history.size())) {
            String role = turn.get("role");
            String content = turn.get("content");
            if (content != null && content.length() > 0) {
                // Truncate long assistant answers to keep prompt lean
                String snippet = content.length() > 200
                        ? content.substring(0, 200) + "..."
                        : content;
                context.append(role.toUpperCase()).append(": ").append(snippet).append("\n");
            }
        }

        // Ask Groq to rewrite the question in full
        String systemPrompt = "You are a question-rewriter for an insurance business chatbot.\n" +
                "Given a conversation history and a follow-up question, rewrite the follow-up\n" +
                "as a fully self-contained question that doesn't rely on previous context.\n\n" +
                "Rules:\n" +
                "- Output ONLY the rewritten question, nothing else.\n" +
                "- Keep it short (one sentence).\n" +
                "- Preserve the original intent exactly.\n" +
                "- If the question is already self-contained, return it unchanged.\n";

        String userMessage = "Conversation history:\n" + context +
                "\nFollow-up question: \"" + question + "\"\n" +
                "Rewritten question:";

        try {
            String rewritten = callGroq(systemPrompt, userMessage).strip()
                    .replaceAll("^\"|\"$", ""); // strip surrounding quotes if any
            logger.info("[NLQuery] Follow-up expanded: '{}' -> '{}'", question, rewritten);
            return rewritten.isEmpty() ? question : rewritten;
        } catch (Exception e) {
            logger.warn("[NLQuery] Follow-up resolution failed, using original: {}", e.getMessage());
            return question; // safe fallback
        }
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    public String ask(String question, String username, List<Map<String, String>> history) {
        if (question == null || question.trim().isEmpty()) {
            return "Please ask a question about your policies, clients, revenue, or messages.";
        }

        Agent agent = agentRepository.findByUsername(username).orElse(null);
        if (agent == null)
            return "Error: Agent not found for username: " + username;
        Long agentId = agent.getId();

        // NEW: Expand follow-up questions using history before classifying
        String resolvedQuestion = resolveFollowUp(question, history);
        logger.info("[NLQuery] Original='{}' -> Resolved='{}'", question, resolvedQuestion);

        // NEW: Classify with history so follow-ups inherit the right intent
        Intent intent = classifyIntent(resolvedQuestion, history);
        logger.info("[NLQuery] Intent={}", intent);

        String sql;
        try {
            sql = generateSql(resolvedQuestion, agentId, intent, history);
            logger.info("[NLQuery] Generated SQL:\n{}", sql);
        } catch (Exception e) {
            logger.error("[NLQuery] SQL generation failed: {}", e.getMessage());
            return "I had trouble understanding that. Try asking about policies, revenue, clients, or messages.";
        }

        String rawResult;
        try {
            rawResult = executeQuery(sql, agentId);
            logger.info("[NLQuery] Raw DB result: {}", rawResult);
        } catch (Exception e) {
            logger.error("[NLQuery] Query execution failed: {}", e.getMessage());
            return "I couldn't fetch that data. Please try rephrasing your question.";
        }

        try {
            // NEW: Pass history to formatter for continuity
            return formatAnswer(resolvedQuestion, rawResult, intent, history);
        } catch (Exception e) {
            logger.error("[NLQuery] Formatting failed: {}", e.getMessage());
            return "Here's the raw data: " + rawResult;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 1 - Intent classification (keyword-based, zero LLM cost)
    // ─────────────────────────────────────────────────────────────────────────

    Intent classifyIntent(String question, List<Map<String, String>> history) {
        // Try to classify the current question first
        Intent current = classifyIntent(question);

        // If we got a clear intent, use it
        if (current != Intent.GENERAL)
            return current;

        // If question is vague/short AND we have history, inherit the last known intent
        if (question.trim().length() < 30 && history != null && history.size() >= 2) {
            // Find last user question in history and classify it
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

    // Keep the original single-arg version for internal use
    Intent classifyIntent(String question) {
        String q = question.toLowerCase();

        if (anyMatch(q, "portfolio", "mix", "breakdown", "distribution", "category",
                "categories", "types of policy", "policy types", "vehicle type",
                "how many types", "split", "composition", "proportion", "pie")) {
            return Intent.PORTFOLIO;
        }
        if (anyMatch(q, "revenue", "income", "earning", "money", "premium", "total premium",
                "how much", "sum", "amount", "financial", "rupee", "payment",
                "collected", "generated")) {
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
                "kpi", "metric", "percentage", "how well", "efficiency", "funnel")) {
            return Intent.PERFORMANCE;
        }

        return Intent.GENERAL;
    }

    private boolean anyMatch(String question, String... keywords) {
        for (String kw : keywords) {
            if (question.contains(kw))
                return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 2 - SQL generation with intent-specific examples
    // ─────────────────────────────────────────────────────────────────────────

    private String generateSql(String question, Long agentId,
            Intent intent, List<Map<String, String>> history) {
        String systemPrompt = buildSqlSystemPrompt(intent);
        String userMessage = buildSqlUserMessage(question, agentId, history);
        return cleanSql(callGroq(systemPrompt, userMessage));
    }

    private String buildSqlSystemPrompt(Intent intent) {
        String base = "You are a PostgreSQL expert. Output ONLY a single valid SELECT query.\n" +
                "Rules:\n" +
                "1. No markdown, no backticks, no explanation -- raw SQL only.\n" +
                "2. SELECT only 4-8 relevant columns, never SELECT *.\n" +
                "3. Always filter by agent_id (the value is in the user message).\n" +
                "4. Use ILIKE for string searches, COALESCE for nullable columns.\n" +
                "5. Use clear aliases: COUNT(*) AS policy_count, SUM(premium) AS total_premium.\n" +
                "6. End with a semicolon.\n" +
                "7. Never use INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE.\n\n" +
                cachedSchema + "\n\n";

        return base + getIntentExamples(intent);
    }

    private String getIntentExamples(Intent intent) {
        switch (intent) {

            case PORTFOLIO:
                return "PORTFOLIO queries -- group policies by type or category.\n\n" +
                        "Q: how does my portfolio mix look\n" +
                        "A: SELECT COALESCE(p.vehicle_type, p.policy_type, 'Other') AS category,\n" +
                        "          COUNT(p.id) AS policy_count,\n" +
                        "          SUM(p.premium) AS total_premium\n" +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND p.status = 'ACTIVE'\n" +
                        "   GROUP BY category ORDER BY policy_count DESC;\n\n" +
                        "Q: breakdown by insurer\n" +
                        "A: SELECT COALESCE(p.insurer_name, 'Unknown') AS insurer,\n" +
                        "          COUNT(p.id) AS policy_count, SUM(p.premium) AS total_premium\n" +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId\n" +
                        "   GROUP BY insurer ORDER BY policy_count DESC;\n\n" +
                        "Q: active vs expired policies\n" +
                        "A: SELECT p.status, COUNT(p.id) AS count, SUM(p.premium) AS total_premium\n" +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId\n" +
                        "   GROUP BY p.status ORDER BY count DESC;\n";

            case REVENUE:
                return "REVENUE queries -- sums, totals, monthly trends.\n\n" +
                        "Q: total revenue this month\n" +
                        "A: SELECT SUM(p.premium) AS total_revenue\n" +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND p.status = 'ACTIVE'\n" +
                        "     AND DATE_TRUNC('month', p.created_at) = DATE_TRUNC('month', CURRENT_DATE);\n\n" +
                        "Q: revenue last month\n" +
                        "A: SELECT SUM(p.premium) AS total_revenue\n" +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId\n" +
                        "     AND p.created_at >= DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 month')\n" +
                        "     AND p.created_at <  DATE_TRUNC('month', CURRENT_DATE);\n\n" +
                        "Q: monthly revenue this year\n" +
                        "A: SELECT TO_CHAR(p.created_at, 'Mon') AS month,\n" +
                        "          EXTRACT(MONTH FROM p.created_at) AS month_num,\n" +
                        "          SUM(p.premium) AS total_revenue\n" +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId\n" +
                        "     AND EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)\n" +
                        "   GROUP BY month, month_num ORDER BY month_num;\n\n" +
                        "Q: top 5 policies by premium\n" +
                        "A: SELECT c.full_name, p.policy_number, p.vehicle_type, p.premium, p.expiry_date\n" +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND p.status = 'ACTIVE'\n" +
                        "   ORDER BY p.premium DESC LIMIT 5;\n";

            case RENEWALS:
                return "RENEWAL queries -- expiring, pending, renewed policies.\n\n" +
                        "Q: policies expiring in the next 30 days\n" +
                        "A: SELECT c.full_name, p.policy_number, p.vehicle_type,\n" +
                        "          p.expiry_date, p.premium, p.renewal_status\n" +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND p.status = 'ACTIVE'\n" +
                        "     AND p.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days'\n" +
                        "   ORDER BY p.expiry_date ASC;\n\n" +
                        "Q: policies expiring this week\n" +
                        "A: SELECT c.full_name, c.phone_number, p.policy_number,\n" +
                        "          p.vehicle_type, p.expiry_date\n" +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND p.status = 'ACTIVE'\n" +
                        "     AND p.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7 days'\n" +
                        "   ORDER BY p.expiry_date ASC;\n\n" +
                        "Q: renewals this month\n" +
                        "A: SELECT COUNT(p.id) AS renewed_count, SUM(p.premium) AS renewed_premium\n" +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId\n" +
                        "     AND p.renewal_status IN ('AUTO_RENEWED','MANUAL_RENEWED','RENEWED')\n" +
                        "     AND DATE_TRUNC('month', p.updated_at) = DATE_TRUNC('month', CURRENT_DATE);\n\n" +
                        "Q: lost clients - expired with no renewal\n" +
                        "A: SELECT c.full_name, c.phone_number, p.policy_number,\n" +
                        "          p.vehicle_type, p.expiry_date, p.premium\n" +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId\n" +
                        "     AND p.status = 'EXPIRED' AND p.renewal_status = 'PENDING'\n" +
                        "   ORDER BY p.expiry_date DESC;\n";

            case CLIENTS:
                return "CLIENT queries -- who, top, lost, contact details.\n\n" +
                        "Q: top 5 clients by premium\n" +
                        "A: SELECT c.full_name, c.email, c.phone_number,\n" +
                        "          COUNT(p.id) AS policy_count, SUM(p.premium) AS total_premium\n" +
                        "   FROM clients c JOIN policies p ON c.id = p.client_id\n" +
                        "   WHERE c.agent_id = :agentId\n" +
                        "   GROUP BY c.id, c.full_name, c.email, c.phone_number\n" +
                        "   ORDER BY total_premium DESC LIMIT 5;\n\n" +
                        "Q: clients with multiple policies\n" +
                        "A: SELECT c.full_name, c.email, c.phone_number, COUNT(p.id) AS policy_count\n" +
                        "   FROM clients c JOIN policies p ON c.id = p.client_id\n" +
                        "   WHERE c.agent_id = :agentId\n" +
                        "   GROUP BY c.id, c.full_name, c.email, c.phone_number\n" +
                        "   HAVING COUNT(p.id) > 1 ORDER BY policy_count DESC;\n\n" +
                        "Q: new clients this month\n" +
                        "A: SELECT c.full_name, c.email, c.phone_number, c.created_at\n" +
                        "   FROM clients c\n" +
                        "   WHERE c.agent_id = :agentId\n" +
                        "     AND DATE_TRUNC('month', c.created_at) = DATE_TRUNC('month', CURRENT_DATE)\n" +
                        "   ORDER BY c.created_at DESC;\n\n" +
                        "Q: clients expiring soon with no whatsapp\n" +
                        "A: SELECT c.full_name, c.email, c.phone_number, p.expiry_date, p.policy_number\n" +
                        "   FROM clients c JOIN policies p ON c.id = p.client_id\n" +
                        "   WHERE c.agent_id = :agentId AND p.status = 'ACTIVE'\n" +
                        "     AND p.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days'\n" +
                        "     AND (c.whatsapp_number IS NULL OR c.whatsapp_number = '')\n" +
                        "   ORDER BY p.expiry_date ASC;\n";

            case MESSAGES:
                return "MESSAGE queries -- SMS/WhatsApp logs, delivery status.\n\n" +
                        "Q: how many messages failed\n" +
                        "A: SELECT ml.channel, COUNT(ml.id) AS failed_count\n" +
                        "   FROM message_logs ml\n" +
                        "   JOIN policies p ON ml.policy_id = p.id\n" +
                        "   JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND ml.status = 'FAILED'\n" +
                        "   GROUP BY ml.channel;\n\n" +
                        "Q: clients with failed messages and no successful retry\n" +
                        "A: SELECT c.full_name, c.phone_number, p.policy_number,\n" +
                        "          p.expiry_date, ml.channel, ml.retry_count, ml.failure_reason\n" +
                        "   FROM clients c\n" +
                        "   JOIN policies p ON c.id = p.client_id\n" +
                        "   JOIN message_logs ml ON p.id = ml.policy_id\n" +
                        "   WHERE c.agent_id = :agentId AND ml.status = 'FAILED'\n" +
                        "     AND NOT EXISTS (\n" +
                        "         SELECT 1 FROM message_logs ml2\n" +
                        "         WHERE ml2.policy_id = p.id\n" +
                        "           AND ml2.channel = ml.channel\n" +
                        "           AND ml2.status = 'SENT'\n" +
                        "     )\n" +
                        "   ORDER BY p.expiry_date ASC;\n\n" +
                        "Q: whatsapp success rate\n" +
                        "A: SELECT COUNT(*) AS total,\n" +
                        "          SUM(CASE WHEN ml.status = 'SENT' THEN 1 ELSE 0 END) AS successful,\n" +
                        "          ROUND(100.0 * SUM(CASE WHEN ml.status = 'SENT' THEN 1 ELSE 0 END) / NULLIF(COUNT(*),0), 2) AS success_rate_pct\n"
                        +
                        "   FROM message_logs ml\n" +
                        "   JOIN policies p ON ml.policy_id = p.id\n" +
                        "   JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId AND ml.channel = 'WHATSAPP';\n";

            case PERFORMANCE:
                return "PERFORMANCE queries -- rates, conversion, KPIs.\n\n" +
                        "Q: renewal conversion rate\n" +
                        "A: SELECT\n" +
                        "       COUNT(*) AS total_expiring,\n" +
                        "       SUM(CASE WHEN p.renewal_status IN ('AUTO_RENEWED','MANUAL_RENEWED','RENEWED') THEN 1 ELSE 0 END) AS renewed,\n"
                        +
                        "       ROUND(100.0 * SUM(CASE WHEN p.renewal_status IN ('AUTO_RENEWED','MANUAL_RENEWED','RENEWED') THEN 1 ELSE 0 END) / NULLIF(COUNT(*),0), 2) AS conversion_rate_pct\n"
                        +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId\n" +
                        "     AND p.expiry_date BETWEEN CURRENT_DATE - INTERVAL '30 days' AND CURRENT_DATE;\n\n" +
                        "Q: policies added this month vs last month\n" +
                        "A: SELECT\n" +
                        "       SUM(CASE WHEN DATE_TRUNC('month', p.created_at) = DATE_TRUNC('month', CURRENT_DATE) THEN 1 ELSE 0 END) AS this_month,\n"
                        +
                        "       SUM(CASE WHEN DATE_TRUNC('month', p.created_at) = DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 month') THEN 1 ELSE 0 END) AS last_month\n"
                        +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId;\n";

            default:
                return "GENERAL queries -- full business overview.\n\n" +
                        "Q: give me a summary\n" +
                        "A: SELECT\n" +
                        "       COUNT(DISTINCT c.id) AS total_clients,\n" +
                        "       COUNT(p.id) AS total_policies,\n" +
                        "       SUM(CASE WHEN p.status = 'ACTIVE' THEN 1 ELSE 0 END) AS active_policies,\n" +
                        "       SUM(CASE WHEN p.status = 'EXPIRED' THEN 1 ELSE 0 END) AS expired_policies,\n" +
                        "       SUM(CASE WHEN p.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days' THEN 1 ELSE 0 END) AS expiring_soon,\n"
                        +
                        "       COALESCE(SUM(CASE WHEN p.status = 'ACTIVE' THEN p.premium END), 0) AS active_premium\n"
                        +
                        "   FROM policies p JOIN clients c ON p.client_id = c.id\n" +
                        "   WHERE c.agent_id = :agentId;\n";
        }
    }

    private String buildSqlUserMessage(String question, Long agentId, List<Map<String, String>> history) {
        StringBuilder msg = new StringBuilder();

        if (history != null && history.size() >= 2) {
            msg.append("Previous Q&A context (last ").append(Math.min(history.size() / 2, 3)).append(" exchanges):\n");

            // Pair up user/assistant turns
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size() - 1; i += 2) {
                Map<String, String> userTurn = history.get(i);
                Map<String, String> assistantTurn = (i + 1 < history.size()) ? history.get(i + 1) : null;

                if ("user".equals(userTurn.get("role"))) {
                    msg.append("Q: ").append(userTurn.get("content")).append("\n");
                    if (assistantTurn != null && "assistant".equals(assistantTurn.get("role"))) {
                        String ans = assistantTurn.get("content");
                        // Truncate long answers
                        msg.append("A: ").append(ans.length() > 150 ? ans.substring(0, 150) + "..." : ans).append("\n");
                    }
                }
            }
            msg.append("\n");
        }

        msg.append("Current question: ").append(question).append("\n")
                .append("Agent ID: ").append(agentId).append("\n")
                .append("SQL:");

        return msg.toString();
    }

    private String cleanSql(String raw) {
        if (raw == null)
            throw new RuntimeException("Groq returned null SQL");
        String cleaned = raw.strip()
                .replaceAll("(?i)```sql\\s*", "")
                .replaceAll("```", "")
                .strip();
        int selectIdx = cleaned.toUpperCase().indexOf("SELECT");
        if (selectIdx > 0)
            cleaned = cleaned.substring(selectIdx);
        int semiIdx = cleaned.indexOf(";");
        if (semiIdx != -1)
            cleaned = cleaned.substring(0, semiIdx + 1);
        return cleaned.strip();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 3 - Execute SQL
    // ─────────────────────────────────────────────────────────────────────────

    private String executeQuery(String sql, Long agentId) {
        String upper = sql.trim().toUpperCase();

        if (!upper.startsWith("SELECT")) {
            throw new IllegalArgumentException("Only SELECT queries allowed. Got: "
                    + sql.substring(0, Math.min(sql.length(), 80)));
        }

        String[] banned = { "INSERT", "UPDATE", "DELETE", "DROP", "TRUNCATE", "ALTER", "CREATE", "GRANT", "EXEC" };
        for (String kw : banned) {
            if (upper.contains(kw))
                throw new IllegalArgumentException("Blocked keyword: " + kw);
        }

        if (!sql.contains(agentId.toString())) {
            throw new IllegalArgumentException("Query must be scoped to agent ID " + agentId);
        }

        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        if (rows.isEmpty())
            return "[]";

        StringBuilder result = new StringBuilder("[\n");
        for (Map<String, Object> row : rows) {
            result.append("  { ");
            List<String> pairs = new ArrayList<>();
            for (Map.Entry<String, Object> col : row.entrySet()) {
                pairs.add(col.getKey() + ": " + col.getValue());
            }
            result.append(String.join(", ", pairs)).append(" },\n");
        }
        result.append("]");
        return result.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 4 - Format answer
    // ─────────────────────────────────────────────────────────────────────────

    private String formatAnswer(String question, String rawResult,
            Intent intent, List<Map<String, String>> history) {

        // Extract last assistant answer for continuity
        String lastAnswer = "";
        if (history != null) {
            for (int i = history.size() - 1; i >= 0; i--) {
                Map<String, String> turn = history.get(i);
                if ("assistant".equals(turn.get("role")) && turn.get("content") != null) {
                    String content = turn.get("content");
                    lastAnswer = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                    break;
                }
            }
        }

        String intentHint = switch (intent) {
            case PORTFOLIO -> "This is about policy type/category distribution.";
            case REVENUE -> "This is about money and premium amounts in Indian Rupees.";
            case RENEWALS -> "This is about policy renewals and expiry dates.";
            case CLIENTS -> "This is about client/customer information.";
            case MESSAGES -> "This is about SMS/WhatsApp reminders and delivery status.";
            case PERFORMANCE -> "This is about business KPIs and conversion rates.";
            default -> "This is a general insurance business question.";
        };

        String systemPrompt = "You are a helpful insurance business analyst assistant.\n" +
                "Convert the raw database result into a clear, friendly, business-focused answer.\n\n" +
                "Rules:\n" +
                "- Use Indian Rupees (INR) formatting (e.g. Rs. 1,23,456).\n" +
                "- Round percentages to 1 decimal place.\n" +
                "- If result is a list, show max 10 items as a readable bullet list.\n" +
                "- If result is [] or empty, say 'No data found' and suggest checking filters.\n" +
                "- Keep it concise: 1 sentence for single values, bullets for lists.\n" +
                "- Never mention SQL, database, tables, or technical terms.\n" +
                "- If this is a follow-up question, naturally continue from the previous answer.\n" +
                "- Do not repeat information already given in the previous answer.\n";

        String previousContext = lastAnswer.isEmpty()
                ? ""
                : "Previous answer (for continuity): \"" + lastAnswer + "\"\n";

        String userMessage = intentHint + "\n"
                + previousContext
                + "Question: \"" + question + "\"\n"
                + "Database result: " + rawResult + "\n"
                + "Answer:";

        return callGroq(systemPrompt, userMessage).strip();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Groq API client
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String callGroq(String systemPrompt, String userMessage) {
        if (groqApiKey == null || groqApiKey.trim().isEmpty()) {
            throw new RuntimeException("Groq API key is not configured.");
        }

        try {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(10_000);
            factory.setReadTimeout(30_000);
            RestTemplate rest = new RestTemplate(factory);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", groqModel);
            body.put("temperature", 0);
            body.put("max_tokens", 1024);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)));

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + groqApiKey);
            headers.set("Content-Type", "application/json");

            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(
                    body, headers);

            Map<?, ?> response = rest.postForObject(groqUrl, entity, Map.class);

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
            logger.error("[NLQuery] Groq error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Groq API error: " + e.getStatusCode());
        } catch (Exception e) {
            logger.error("[NLQuery] Groq call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to reach Groq: " + e.getMessage());
        }
    }
}