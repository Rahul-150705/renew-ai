package com.renewai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto implements Serializable {
    private long totalPolicies;
    private long expiringSoonCount;
    private double renewalRate;
    private double annualPremium;
    private long pendingRenewals;
    private long claimsRaised;
    private double policiesGrowthPercentage;
    
    // Advanced Metrics
    private double conversionRate; // renewed / total expiring this month
    private double whatsappSuccessRate;
    private double smsSuccessRate;
    private long failedMessagesCount;
    private long exhaustedRetriesCount; // Needs manual action (retryCount >= 3)
    private long lostClientsCount; // EXPIRED and PENDING
    private double revenueGrowthPercentage;
    private long renewedThisMonth;
}
