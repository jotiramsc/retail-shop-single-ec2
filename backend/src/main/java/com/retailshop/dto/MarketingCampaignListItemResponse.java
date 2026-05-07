package com.retailshop.dto;

import com.retailshop.enums.MarketingCampaignType;
import com.retailshop.enums.MarketingPlatform;
import com.retailshop.enums.MarketingWorkflowStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class MarketingCampaignListItemResponse {
    private UUID id;
    private String campaignName;
    private MarketingCampaignType campaignType;
    private List<MarketingPlatform> targetPlatforms;
    private MarketingWorkflowStatus status;
    private String createdBy;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long contentCount;
    private long pendingApprovalCount;
    private long approvedCount;
    private long scheduledCount;
    private long publishedCount;
    private long failedCount;
    private LocalDateTime nextScheduledAt;
}
