package com.renewai.controller;

import com.renewai.service.NLQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentQueryController {

    @Autowired private NLQueryService nlQueryService;

    @SuppressWarnings("unchecked")
    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> ask(
            @RequestBody Map<String, Object> body,
            Authentication auth) {

        String question = (String) body.get("question");
        java.util.List<Map<String, String>> history = (java.util.List<Map<String, String>>) body.get("history");

        String answer = nlQueryService.ask(
            question,
            auth.getName(),
            history
        );
        
        if (answer == null) {
            answer = "I'm sorry, I couldn't generate an answer at this time.";
        }
        
        return ResponseEntity.ok(Map.of("answer", answer));
    }
}
