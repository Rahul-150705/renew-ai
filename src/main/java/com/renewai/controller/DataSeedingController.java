package com.renewai.controller;

import com.renewai.entity.Agent;
import com.renewai.entity.Client;
import com.renewai.entity.Policy;
import com.renewai.repository.AgentRepository;
import com.renewai.repository.ClientRepository;
import com.renewai.repository.PolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/seed")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DataSeedingController {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @PostMapping("/policies")
    public ResponseEntity<?> seedPolicies(Authentication authentication) {
        String username = authentication.getName();
        Optional<Agent> agentOpt = agentRepository.findByUsername(username);

        if (!agentOpt.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Agent not found"));
        }

        Agent agent = agentOpt.get();
        List<String> vehicleTypes = Arrays.asList("Auto", "Home", "Health", "Life", "Travel");
        List<String> statuses = Arrays.asList("ACTIVE", "ACTIVE", "ACTIVE", "EXPIRED", "RENEWED");
        List<String> firstNames = Arrays.asList("John", "Jane", "Michael", "Sarah", "David", "Emily", "Robert",
                "Jessica", "William", "Olivia");
        List<String> lastNames = Arrays.asList("Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller",
                "Davis", "Rodriguez", "Martinez");

        Random random = new Random();
        List<Policy> createdPolicies = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            // Create or pick a client
            String firstName = firstNames.get(random.nextInt(firstNames.size()));
            String lastName = lastNames.get(random.nextInt(lastNames.size()));
            String fullName = firstName + " " + lastName;
            String email = firstName.toLowerCase() + "." + lastName.toLowerCase() + i + "@example.com";

            Client client = new Client();
            client.setFullName(fullName);
            client.setEmail(email);
            client.setPhoneNumber("+919" + (100000000 + random.nextInt(900000000)));
            client.setWhatsappNumber(random.nextBoolean() ? client.getPhoneNumber() : null);
            client.setAgent(agent);
            client = clientRepository.save(client);

            // Create policy
            Policy policy = new Policy();
            policy.setClient(client);
            policy.setPolicyNumber("POL-" + (100000 + random.nextInt(900000)));
            policy.setVehicleType(vehicleTypes.get(random.nextInt(vehicleTypes.size())));
            policy.setPolicyType("INSURANCE");
            policy.setInsurerName("Global Protect " + (random.nextInt(5) + 1));

            // Set random expiry date (some in the past, some soon, some far)
            int daysOffset = random.nextInt(100) - 20; // -20 to +80 days
            LocalDate expiryDate = LocalDate.now().plusDays(daysOffset);
            policy.setExpiryDate(expiryDate);
            policy.setStartDate(expiryDate.minusYears(1));

            policy.setPremium(new BigDecimal(500 + random.nextInt(2000)));
            policy.setStatus(statuses.get(random.nextInt(statuses.size())));

            if ("EXPIRED".equals(policy.getStatus())) {
                policy.setExpiryDate(LocalDate.now().minusDays(random.nextInt(30) + 1));
            } else if ("RENEWED".equals(policy.getStatus())) {
                policy.setRenewalStatus("MANUAL_RENEWED");
            }

            policy.setRegistrationNumber("TN-" + (10 + random.nextInt(90)) + "-AB-" + (1000 + random.nextInt(9000)));

            createdPolicies.add(policyRepository.save(policy));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Successfully seeded 20 policies",
                "count", createdPolicies.size()));
    }
}
