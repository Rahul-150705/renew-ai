package com.renewai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Client Entity - Represents insurance policy holders
 * Each client is managed by an agent
 */
@Entity
@Table(name = "clients")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Client {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String fullName;
    
    @Column(nullable = false, unique = true, length = 100)
    private String email;
    
    // Phone number for SMS notifications (must include country code)
    @Column(nullable = false, length = 15)
    private String phoneNumber;
    
    @Column(length = 255)
    private String address;
    
    // Many clients can be managed by one agent
    // FIXED: Added @JsonIgnore to prevent circular serialization
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;
    
    // One client can have multiple insurance policies
    // FIXED: Added @JsonIgnore to prevent circular serialization
    @JsonIgnore
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Policy> policies;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}