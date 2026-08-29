package com.renewai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renewai.entity.Agent;
import com.renewai.repository.AgentRepository;
import com.renewai.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final AgentRepository agentRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public GoogleOAuthSuccessHandler(AgentRepository agentRepository, JwtUtil jwtUtil) {
        this.agentRepository = agentRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();
        String email = googleUser.getAttribute("email");

        if (!StringUtils.hasText(email)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Google email not found for this account.");
            return;
        }

        Agent agent = agentRepository.findByEmail(email)
                .orElseGet(() -> createGoogleAgent(googleUser, email));

        String token = jwtUtil.generateToken(agent.getUsername(), agent.getId());

        String redirectUrl = frontendUrl + "/login?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&agentId=" + agent.getId()
                + "&username=" + URLEncoder.encode(agent.getUsername(), StandardCharsets.UTF_8)
                + "&fullName=" + URLEncoder.encode(agent.getFullName(), StandardCharsets.UTF_8)
                + "&email=" + URLEncoder.encode(agent.getEmail(), StandardCharsets.UTF_8);

        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, redirectUrl);
        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        response.setHeader("Access-Control-Expose-Headers", HttpHeaders.AUTHORIZATION + ", " + HttpHeaders.LOCATION);
        response.sendRedirect(redirectUrl);
    }

    private Agent createGoogleAgent(OAuth2User googleUser, String email) {
        String baseUsername = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String username = baseUsername;
        int suffix = 1;

        while (agentRepository.existsByUsername(username)) {
            username = baseUsername + suffix;
            suffix++;
        }

        String fullName = googleUser.getAttribute("name");
        if (!StringUtils.hasText(fullName)) {
            fullName = baseUsername;
        }

        Agent agent = new Agent();
        agent.setUsername(username);
        agent.setEmail(email);
        agent.setFullName(fullName);
        agent.setPassword(new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString()));
        agent.setActive(true);

        return agentRepository.save(agent);
    }
}
