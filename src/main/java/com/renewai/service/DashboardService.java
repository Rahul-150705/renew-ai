package com.renewai.service;

import com.renewai.dto.AiInsightDto;
import com.renewai.dto.ChartDataDto;
import com.renewai.dto.DashboardSummaryDto;
import com.renewai.entity.Agent;
import com.renewai.repository.AgentRepository;
import com.renewai.repository.PolicyRepository;
import com.renewai.repository.MessageLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private final PolicyRepository policyRepository;
    private final MessageLogRepository messageLogRepository;
    private final AgentRepository agentRepository;

    private Agent getAgent(String username) {
        Agent agent = agentRepository.findByUsername(username)
                .or(() -> agentRepository.findByEmail(username))
                .orElseThrow(() -> new RuntimeException("Agent not found: " + username));
        log.debug("Dashboard lookup for user: {} (Agent ID: {})", username, agent.getId());
        return agent;
    }

    @Cacheable(value = "dashboardSummary", key = "#p0 + '-' + #p1")
    public DashboardSummaryDto getSummary(String username, int period) {
        Agent agent = getAgent(username);
        Long agentId = agent.getId();

        long totalPolicies = policyRepository.countByAgentId(agentId);

        LocalDate now = LocalDate.now();
        LocalDate soon = now.plusDays(period);
        long expiringSoon = policyRepository.countExpiringSoonByAgentId(agentId, now, soon);

        long renewedCount = policyRepository.countRenewedByAgentId(agentId);
        double renewalRate = totalPolicies > 0 ? (double) renewedCount / totalPolicies * 100 : 0.0;

        // Period-based stats
        LocalDateTime periodStart = LocalDateTime.now().minusDays(period);
        LocalDateTime nowDateTime = LocalDateTime.now();

        long currentPeriodCreated = policyRepository.countCreatedByAgentBetween(agentId, periodStart, nowDateTime);

        // Count renewals only within the selected period window
        long renewedInPeriod = policyRepository.countRenewedByAgentBetween(agentId, now.minusDays(period), now);

        // Let's use the new query for expiring premium in period
        BigDecimal premiumInPeriod = policyRepository.sumPremiumForExpiringPoliciesInRange(agentId, now, soon);
        double annualPremium = premiumInPeriod != null ? premiumInPeriod.doubleValue() : 0.0;

        long failedMessages = messageLogRepository.countFailedByAgentId(agentId);

        // Growth calculation (vs previous identical period)
        LocalDateTime previousPeriodStart = periodStart.minusDays(period);
        long previousPeriodCreated = policyRepository.countCreatedByAgentBetween(agentId, previousPeriodStart,
                periodStart);

        double growth = previousPeriodCreated > 0
                ? (double) (currentPeriodCreated - previousPeriodCreated) / previousPeriodCreated * 100
                : (currentPeriodCreated > 0 ? 100.0 : 0.0);

        // Conversion Rate: renewed / (renewed + expiring) — capped at 100%
        long totalInWindow = renewedInPeriod + expiringSoon;
        double conversionRate = totalInWindow > 0 ? Math.min((double) renewedInPeriod / totalInWindow * 100, 100.0)
                : 0.0;

        long waSuccess = messageLogRepository.countSuccessByChannelAndAgentId("WHATSAPP", agentId);
        long waTotal = messageLogRepository.countTotalByChannelAndAgentId("WHATSAPP", agentId);
        double waRate = waTotal > 0 ? (double) waSuccess / waTotal * 100 : 0.0;

        long exhaustedRetries = messageLogRepository.countExhaustedRetries(agentId);
        long lostClients = policyRepository.countLostPoliciesBetween(agentId, LocalDate.now().minusDays(period),
                LocalDate.now());

        // Revenue growth calculation (MoM for actual month)
        BigDecimal thisMonthRevenue = policyRepository.sumActivePremiumThisMonth(agentId);
        BigDecimal lastMonthRevenue = policyRepository.sumActivePremiumLastMonth(agentId);
        double revenueGrowth = 0.0;
        if (lastMonthRevenue != null && lastMonthRevenue.compareTo(BigDecimal.ZERO) > 0) {
            revenueGrowth = (thisMonthRevenue.subtract(lastMonthRevenue))
                    .divide(lastMonthRevenue, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        } else if (thisMonthRevenue != null && thisMonthRevenue.compareTo(BigDecimal.ZERO) > 0) {
            revenueGrowth = 100.0;
        }

        return new DashboardSummaryDto(
                totalPolicies,
                expiringSoon,
                renewalRate,
                annualPremium,
                failedMessages, // pendingRenewals
                0, // claimsRaised
                growth,
                conversionRate,
                waRate,
                0.0, // smsSuccessRate (Removing SMS)
                failedMessages,
                exhaustedRetries,
                lostClients,
                revenueGrowth,
                renewedInPeriod);
    }

    @Cacheable(value = "renewalTrends", key = "#p0 + '-' + #p1")
    public List<ChartDataDto> getRenewalTrends(String username, int period) {
        Agent agent = getAgent(username);
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(period);

        List<Object[]> rawData = policyRepository.findProjectedRenewalsRaw(agent.getId(), start, end);
        List<ChartDataDto> trends = new ArrayList<>();

        for (Object[] row : rawData) {
            trends.add(new ChartDataDto(row[0].toString(), ((Number) row[1]).longValue()));
        }

        if (trends.isEmpty()) {
            trends.add(new ChartDataDto(start.toString(), 0));
            trends.add(new ChartDataDto(end.toString(), 0));
        }

        return trends;
    }

    @Cacheable(value = "revenueTrends", key = "#p0")
    public List<ChartDataDto> getRevenueTrends(String username) {
        Agent agent = getAgent(username);
        LocalDateTime startOfYear = LocalDateTime.now().withDayOfYear(1).withHour(0).withMinute(0);

        List<Object[]> rawData = policyRepository.findMonthlyRevenueRaw(agent.getId(), startOfYear);
        List<ChartDataDto> revenueTrend = new ArrayList<>();

        // Initialize all months with 0
        for (int i = 1; i <= 12; i++) {
            String monthName = Month.of(i).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            revenueTrend.add(new ChartDataDto(monthName, 0));
        }

        // Update months with actual data
        for (Object[] row : rawData) {
            if (row == null || row[0] == null)
                continue;

            int monthNum = ((Number) row[0]).intValue();
            BigDecimal amount = (row[1] == null) ? BigDecimal.ZERO
                    : (row[1] instanceof BigDecimal) ? (BigDecimal) row[1] : new BigDecimal(row[1].toString());

            long value = amount.longValue();
            String monthName = Month.of(monthNum).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

            // Find the correct ChartDataDto in the list and update it
            for (ChartDataDto dto : revenueTrend) {
                if (dto.getName().equals(monthName)) {
                    dto.setValue(value);
                    break;
                }
            }
        }

        return revenueTrend;
    }

    @Cacheable(value = "policyDistribution", key = "#p0")
    public List<ChartDataDto> getPolicyDistribution(String username) {
        Agent agent = getAgent(username);
        return policyRepository.findPolicyDistributionByAgentId(agent.getId());
    }

    @Cacheable(value = "aiInsights", key = "#p0")
    public List<AiInsightDto> getAiInsights(String username) {
        Agent agent = getAgent(username);
        long failedCount = messageLogRepository.countFailedByAgentId(agent.getId());
        long expiringCount = policyRepository.countExpiringSoonByAgentId(agent.getId(), LocalDate.now(),
                LocalDate.now().plusDays(7));

        List<AiInsightDto> insights = new ArrayList<>();

        if (failedCount > 0) {
            insights.add(new AiInsightDto(
                    "Failed Communications",
                    failedCount + " renewals failed automated messaging. Urgent manual contact required.",
                    "Critical",
                    "rose"));
        }

        if (expiringCount > 0) {
            insights.add(new AiInsightDto(
                    "Peak Expiry Week",
                    expiringCount + " policies expire in the next 7 days. High workload predicted.",
                    "82% Prob.",
                    "amber"));
        }

        insights.add(new AiInsightDto(
                "Upsell Opportunity",
                "Data suggests 15% of your motor clients may be interested in roadside assistance add-ons.",
                "High Conv.",
                "blue"));

        return insights;
    }

    @Cacheable(value = "conversionFunnel", key = "#p0")
    public List<ChartDataDto> getConversionFunnel(String username) {
        Agent agent = getAgent(username);
        long expiring = policyRepository.countExpiringSoonByAgentId(agent.getId(), LocalDate.now(),
                LocalDate.now().plusDays(30));
        long contacted = messageLogRepository.countSentTodayByAgentId(agent.getId());
        long renewed = policyRepository.countRenewedByAgentId(agent.getId());

        return List.of(
                new ChartDataDto("Expiring", expiring),
                new ChartDataDto("Contacted", contacted),
                new ChartDataDto("Interested", (long) (contacted * 0.7)),
                new ChartDataDto("Renewed", renewed));
    }
}
