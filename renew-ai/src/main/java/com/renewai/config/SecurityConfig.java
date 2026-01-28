package com.renewai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security Configuration for the application
 * Configures JWT-based stateless authentication
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    /**
     * Configure security filter chain
     * - Public endpoints: /api/auth/** (login, register)
     * - Protected endpoints: Everything else
     * - Stateless session management (no session cookies)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF as we're using JWT (stateless)
            .csrf(csrf -> csrf.disable())
            
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints (no authentication required)
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/error").permitAll()
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            
            // Stateless session management (no session cookies)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Add JWT filter before UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    /**
     * Password encoder bean
     * Uses BCrypt hashing algorithm with strength 12
     * @return BCryptPasswordEncoder instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}