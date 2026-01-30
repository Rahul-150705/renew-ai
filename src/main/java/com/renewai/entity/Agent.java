package com.renewai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent Entity - Represents insurance agents who manage clients
 * Passwords are stored using BCrypt hashing for security
 */
@Entity
@Table(name = "agents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Agent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 100)
    private String username;
    
    @Column(nullable = false, unique = true, length = 100)
    private String email;
    
    // BCrypt hashed password (stored as 60 character hash)
    @Column(nullable = false, length = 100)
    private String password;
    
    @Column(nullable = false, length = 100)
    private String fullName;
    
    @Column(length = 15)
    private String phoneNumber;
    
    @Column(nullable = false)
    private Boolean active = true;
    
    // One agent can manage multiple clients
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Client> clients;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}