package com.renewai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

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
            // Enable CORS with our custom configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Disable CSRF as we are using JWT (stateless)
            .csrf(csrf -> csrf.disable())
            
            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/error").permitAll()
                
                // Allow preflight OPTIONS requests for all endpoints
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            
            // Stateless session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Add JWT filter before UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration
     * - Allows any localhost origin
     * - Supports credentials (cookies/auth headers)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow any localhost origin
        configuration.addAllowedOriginPattern("http://localhost:*");

        // Allow common HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Allow common headers
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));

        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);

        // Max age for preflight requests
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * Password encoder bean
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
