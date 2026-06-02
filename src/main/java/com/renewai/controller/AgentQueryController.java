package com.renewai.controller;

import com.renewai.service.NLQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentQueryController {

    @Autowired
    private NLQueryService nlQueryService;

    @Autowired
    private com.renewai.service.RateLimitingService rateLimitingService;

    @SuppressWarnings("unchecked")
    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(
            @RequestBody Map<String, Object> body,
            Authentication auth) {

        io.github.bucket4j.Bucket bucket = rateLimitingService.resolveAgentQueryBucket(auth.getName());
        if (!bucket.tryConsume(1)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Too many questions. Please wait a moment before trying again.");
            return ResponseEntity.status(429).body(error);
        }

        String question = (String) body.get("question");
        List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");

        // Reconstruct SessionMemory from frontend payload
        String lastSql = (String) body.get("lastSql");
        String lastTopic = (String) body.get("lastTopic");
        String lastResultSummary = (String) body.get("lastResultSummary");
        List<String> lastCategories = (List<String>) body.get("lastCategories");

        NLQueryService.SessionMemory sessionMemory = null;
        if (lastSql != null || lastTopic != null) {
            sessionMemory = new NLQueryService.SessionMemory(
                    lastTopic, lastCategories, lastResultSummary, lastSql);
        }

        NLQueryService.AskResult result = nlQueryService.ask(
                question,
                auth.getName(),
                history,
                sessionMemory);

        String answer = result.answer();
        if (answer == null) {
            answer = "I'm sorry, I couldn't generate an answer at this time.";
        }

        // Return answer, sql, and full sessionMemory so frontend can send it back
        Map<String, Object> response = new HashMap<>();
        response.put("answer", answer);
        response.put("sql", result.sql());
        response.put("data", result.data());
        if (result.sessionMemory() != null) {
            response.put("lastTopic", result.sessionMemory().lastTopic());
            response.put("lastCategories", result.sessionMemory().lastCategories());
            response.put("lastResultSummary", result.sessionMemory().lastResultSummary());
            response.put("lastSql", result.sessionMemory().lastSql());
        }

        return ResponseEntity.ok(response);
    }
}