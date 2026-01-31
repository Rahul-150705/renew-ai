package com.renewai.controller;

import com.renewai.dto.ClientRequest;
import com.renewai.entity.Client;
import com.renewai.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client Controller
 * Handles client management operations
 * SECURED ENDPOINT - JWT required
 * FIXED: Removed @CrossOrigin as CORS is now configured globally in SecurityConfig
 */
@RestController
@RequestMapping("/api/clients")
public class ClientController {
    
    @Autowired
    private ClientService clientService;
    
    /**
     * Create a new client
     * POST /api/clients
     * @param clientRequest client details
     * @param authentication JWT authentication
     * @return created client
     */
    @PostMapping
    public ResponseEntity<?> createClient(
            @Valid @RequestBody ClientRequest clientRequest,
            Authentication authentication) {
        
        try {
            String username = authentication.getName();
            Client client = clientService.createClient(clientRequest, username);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(client);
            
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * Get all clients for the authenticated agent
     * GET /api/clients
     * @param authentication JWT authentication
     * @return list of clients
     */
    @GetMapping
    public ResponseEntity<List<Client>> getMyClients(Authentication authentication) {
        String username = authentication.getName();
        List<Client> clients = clientService.getClientsByAgent(username);
        return ResponseEntity.ok(clients);
    }
    
    /**
     * Get client by ID
     * GET /api/clients/{id}
     * @param id client ID
     * @return client details
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getClientById(@PathVariable Long id) {
        try {
            Client client = clientService.getClientById(id);
            return ResponseEntity.ok(client);
            
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
}