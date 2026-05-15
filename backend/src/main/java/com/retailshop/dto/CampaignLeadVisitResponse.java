package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class CampaignLeadVisitResponse {
    private UUID id;
    private UUID campaignId;
    private String source;
    private UUID productId;
    private UUID offerId;
    private String sessionId;
    private LocalDateTime timestamp;
    private LocalDateTime createdAt;
}
