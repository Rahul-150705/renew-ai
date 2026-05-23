package com.renewai.controller;

import com.renewai.dto.*;
import com.renewai.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDto> getSummary(Authentication authentication, @RequestParam(defaultValue = "30") int period) {
        return ResponseEntity.ok(dashboardService.getSummary(authentication.getName(), period));
    }

    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping("/renewal-trends")
    public ResponseEntity<List<ChartDataDto>> getRenewalTrends(Authentication authentication, @RequestParam(defaultValue = "30") int period) {
        return ResponseEntity.ok(dashboardService.getRenewalTrends(authentication.getName(), period));
    }

    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping("/policy-distribution")
    public ResponseEntity<List<ChartDataDto>> getPolicyDistribution(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getPolicyDistribution(authentication.getName()));
    }

    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping("/ai-insights")
    public ResponseEntity<List<AiInsightDto>> getAiInsights(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getAiInsights(authentication.getName()));
    }

    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping("/revenue-trends")
    public ResponseEntity<List<ChartDataDto>> getRevenueTrends(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getRevenueTrends(authentication.getName()));
    }

    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping("/conversion-funnel")
    public ResponseEntity<List<ChartDataDto>> getConversionFunnel(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getConversionFunnel(authentication.getName()));
    }
}
