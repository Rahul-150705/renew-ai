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

    @Column(nullable = false, length = 50)
    private String policyType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal premium;

    @Column(length = 20)
    private String premiumFrequency = "YEARLY";

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    // Path to the stored PDF file (nullable - not all policies have a PDF)
    @Column(length = 500)
    private String pdfFilePath;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

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