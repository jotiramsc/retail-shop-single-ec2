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
    private UUID id;
    private UUID campaignId;
    private String campaignName;
    private CampaignType channel;
    private CampaignStatus status;
    private String content;
    private String mediaUrl;
    private String platformResponseId;
    private String errorMessage;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
