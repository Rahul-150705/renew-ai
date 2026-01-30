package com.renewai.service;

import com.renewai.dto.ClientDTO;
import com.renewai.dto.ClientRequest;
import com.renewai.entity.Agent;
import com.renewai.entity.Client;
import com.renewai.repository.AgentRepository;
import com.renewai.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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
     * Convert Client entity to DTO
     */
    private ClientDTO convertToDTO(Client client) {
        ClientDTO dto = new ClientDTO();
        dto.setId(client.getId());
        dto.setFullName(client.getFullName());
        dto.setEmail(client.getEmail());
        dto.setPhoneNumber(client.getPhoneNumber());
        dto.setAddress(client.getAddress());
        dto.setAgentId(client.getAgent().getId());
        dto.setCreatedAt(client.getCreatedAt());
        dto.setUpdatedAt(client.getUpdatedAt());
        return dto;
    }
    
    /**
     * Create a new client for an agent
     * @param clientRequest client details
     * @param username username of the agent creating the client
     * @return created client DTO
     */
    @Transactional
    public ClientDTO createClient(ClientRequest clientRequest, String username) {
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
        
        // Save and return client DTO
        Client savedClient = clientRepository.save(client);
        return convertToDTO(savedClient);
    }
    
    /**
     * Get all clients managed by an agent
     * @param username username of the agent
     * @return list of client DTOs
     */
    public List<ClientDTO> getClientsByAgent(String username) {
        Agent agent = agentRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        
        List<Client> clients = clientRepository.findByAgent(agent);
        return clients.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Get client by ID
     * @param clientId client ID
     * @return client DTO
     */
    public ClientDTO getClientById(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));
        return convertToDTO(client);
    }
    
    /**
     * Get client entity by ID (for internal use)
     * @param clientId client ID
     * @return client entity
     */
    public Client getClientEntityById(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));
    }
    
    /**
     * Get client by email
     * @param email client email
     * @return client DTO
     */
    public ClientDTO getClientByEmail(String email) {
        Client client = clientRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Client not found"));
        return convertToDTO(client);
    }
}