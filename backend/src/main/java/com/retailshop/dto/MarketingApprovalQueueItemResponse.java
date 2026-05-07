package com.retailshop.dto;

import com.retailshop.enums.MarketingPlatform;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class MarketingApprovalQueueItemResponse {
    private UUID contentId;
    private UUID campaignId;
    private String campaignName;
    private MarketingPlatform platform;
    private String captionText;
    private String hashtags;
    private String callToAction;
    private String imagePrompt;
    private String imageUrl;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
