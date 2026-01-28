package com.renewai.service;

import com.renewai.dto.ClientRequest;
import com.renewai.entity.Agent;
import com.renewai.entity.Client;
import com.renewai.repository.AgentRepository;
import com.renewai.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for client management operations
 * Handles creating and retrieving clients
 */
@Service
public class ClientService {
    
    @Autowired
    private ClientRepository clientRepository;
    
    @Autowired
    private AgentRepository agentRepository;
    
    /**
     * Create a new client for an agent
     * @param clientRequest client details
     * @param username username of the agent creating the client
     * @return created client
     */
    @Transactional
    public Client createClient(ClientRequest clientRequest, String username) {
        // Find the agent by username
        Agent agent = agentRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        
        // Check if email already exists
        if (clientRepository.existsByEmail(clientRequest.getEmail())) {
            throw new RuntimeException("Client with this email already exists");
        }
        
        // Create new client
        Client client = new Client();
        client.setFullName(clientRequest.getFullName());
        client.setEmail(clientRequest.getEmail());
        client.setPhoneNumber(clientRequest.getPhoneNumber());
        client.setAddress(clientRequest.getAddress());
        client.setAgent(agent);
        
        // Save and return client
        return clientRepository.save(client);
    }
    
    /**
     * Get all clients managed by an agent
     * @param username username of the agent
     * @return list of clients
     */
    public List<Client> getClientsByAgent(String username) {
        Agent agent = agentRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        
        return clientRepository.findByAgent(agent);
    }
    
    /**
     * Get client by ID
     * @param clientId client ID
     * @return client
     */
    public Client getClientById(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));
    }
    
    /**
     * Get client by email
     * @param email client email
     * @return client
     */
    public Client getClientByEmail(String email) {
        return clientRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Client not found"));
    }
}