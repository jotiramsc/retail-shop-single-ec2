package com.retailshop.dto;

import com.retailshop.enums.CampaignStatus;
import com.retailshop.enums.CampaignType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class CampaignLogResponse {
    private UUID campaignId;
    private String campaignName;
    private CampaignType campaignType;
    private UUID customerId;
    private String customerName;
    private String customerMobile;
    private CampaignStatus status;
    private LocalDateTime createdAt;
}
