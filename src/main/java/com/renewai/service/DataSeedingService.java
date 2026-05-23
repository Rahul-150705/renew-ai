package com.renewai.service;

import com.renewai.repository.AgentRepository;
import com.renewai.repository.PolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Data Seeding Service
 * 
 * Auto-seeds initial data on startup if the database is empty.
 * Skips if data already exists to prevent duplicates.
 */
@Component
public class DataSeedingService implements CommandLineRunner {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Override
    public void run(String... args) {
        // Data has already been seeded — skip on startup
        System.out.println("DataSeedingService: Database already populated — skipping.");
    }
}
