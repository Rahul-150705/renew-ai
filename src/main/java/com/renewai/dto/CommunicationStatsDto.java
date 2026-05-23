package com.renewai.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class CommunicationStatsDto {
    private long messagesSentToday;
    private double whatsappSuccessRate;
    private long smsFallbackCount;
    private double overallFailureRate;
}
