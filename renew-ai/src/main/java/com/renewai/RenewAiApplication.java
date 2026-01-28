package com.renewai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Renew AI - Insurance Renewal Automation Platform
 * 
 * Main Spring Boot Application
 * 
 * Features:
 * - JWT-based authentication for agents
 * - Client and policy management
 * - Automated renewal reminders using Spring Scheduler
 * - SMS integration (Twilio/Mock)
 * - Message deduplication using MessageLog
 * 
 * @EnableScheduling enables the scheduled tasks (renewal reminder job)
 */
@SpringBootApplication
@EnableScheduling
public class RenewAiApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(RenewAiApplication.class, args);
        System.out.println("===========================================");
        System.out.println("   Renew AI - Server Started Successfully");
        System.out.println("   Port: 8080");
        System.out.println("   Scheduler: Enabled (Daily at 9:00 AM)");
        System.out.println("===========================================");
    }
}