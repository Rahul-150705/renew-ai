package com.renewai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Policy Entity - Represents insurance policies for clients
 * Tracks policy details and expiry dates for renewal reminders
 */
@Entity
@Table(name = "policies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Policy {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String policyNumber;
    
    // Type of insurance: LIFE, HEALTH, AUTO, HOME, etc.
    @Column(nullable = false, length = 50)
    private String policyType;
    
    @Column(nullable = false)
    private LocalDate startDate;
    
    // Critical field for renewal reminders
    @Column(nullable = false)
    private LocalDate expiryDate;
    
    // Premium amount
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal premium;
    
    // Premium frequency: MONTHLY, QUARTERLY, YEARLY
    @Column(length = 20)
    private String premiumFrequency = "YEARLY";
    
    @Column(length = 500)
    private String description;
    
    // Policy status: ACTIVE, EXPIRED, RENEWED, CANCELLED
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
    
    // Many policies belong to one client
    // FIXED: Added @JsonIgnore to prevent circular serialization
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;
    
    // Track all messages sent for this policy
    // FIXED: Added @JsonIgnore to prevent circular serialization
    @JsonIgnore
    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MessageLog> messageLogs;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}