package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class OmnichannelLeadResponse {
    private UUID id;
    private UUID leadId;
    private String channel;
    private String externalUserId;
    private String customerName;
    private String mobile;
    private String sourceCampaign;
    private String productInterest;
    private String latestMessage;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
