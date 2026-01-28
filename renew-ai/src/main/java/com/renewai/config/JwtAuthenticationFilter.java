package com.renewai.config;

import com.renewai.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * JWT Authentication Filter
 * Intercepts every request and validates JWT token from Authorization header
 * Runs once per request to avoid duplicate validation
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * Filter logic to extract and validate JWT token
     * Sets authentication in SecurityContext if token is valid
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        // Extract Authorization header
        final String authorizationHeader = request.getHeader("Authorization");
        
        String username = null;
        String jwt = null;
        
        // Check if header exists and starts with "Bearer "
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7); // Remove "Bearer " prefix
            
            try {
                username = jwtUtil.extractUsername(jwt);
            } catch (Exception e) {
                logger.error("Error extracting username from JWT: " + e.getMessage());
            }
        }
        
        // Validate token and set authentication
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            
            if (jwtUtil.validateToken(jwt, username)) {
                // Create authentication token
                UsernamePasswordAuthenticationToken authenticationToken = 
                    new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>());
                
                authenticationToken.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );
                
                // Set authentication in security context
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                
                logger.debug("JWT validated successfully for user: " + username);
            }
        }
        
        // Continue filter chain
        filterChain.doFilter(request, response);
    }
}