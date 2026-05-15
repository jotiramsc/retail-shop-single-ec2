package com.retailshop.dto;

import com.retailshop.enums.MarketingContentStatus;
import com.retailshop.enums.MarketingPlatform;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class MarketingContentResponse {
    private UUID id;
    private UUID campaignId;
    private MarketingPlatform platform;
    private String captionText;
    private String hashtags;
    private String callToAction;
    private String imagePrompt;
    private String imageUrl;
    private MarketingContentStatus status;
    private String rejectionReason;
    private String deliveryStatus;
    private String deliveryReport;
    private String deliveryError;
    private LocalDateTime scheduledAt;
    private LocalDateTime publishedAt;
    private String externalPostId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ApprovalHistoryResponse> approvalHistory;
}
