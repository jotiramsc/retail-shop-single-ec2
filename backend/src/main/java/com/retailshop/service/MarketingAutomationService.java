package com.retailshop.service;

import com.retailshop.dto.MarketingAnalyticsResponse;
import com.retailshop.dto.MarketingApprovalQueueItemResponse;
import com.retailshop.dto.MarketingApprovalRequest;
import com.retailshop.dto.MarketingCampaignListItemResponse;
import com.retailshop.dto.MarketingCampaignRequest;
import com.retailshop.dto.MarketingCampaignResponse;
import com.retailshop.dto.MarketingCampaignSuggestionResponse;
import com.retailshop.dto.MarketingContentResponse;
import com.retailshop.dto.MarketingContentUpdateRequest;
import com.retailshop.dto.MarketingRejectRequest;
import com.retailshop.dto.MarketingScheduleRequest;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.enums.MarketingCampaignType;
import com.retailshop.enums.MarketingPlatform;
import com.retailshop.enums.MarketingWorkflowStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MarketingAutomationService {
    MarketingCampaignResponse createCampaign(MarketingCampaignRequest request, String actor);

    MarketingCampaignResponse generateCampaign(UUID campaignId, String actor);

    PaginatedResponse<MarketingCampaignListItemResponse> getCampaigns(
            MarketingWorkflowStatus status,
            MarketingPlatform platform,
            MarketingCampaignType campaignType,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    );

    MarketingCampaignResponse getCampaign(UUID campaignId);

    void deleteCampaign(UUID campaignId);

    List<MarketingCampaignSuggestionResponse> getCampaignSuggestions(int daysAhead);

    MarketingContentResponse updateContent(UUID contentId, MarketingContentUpdateRequest request, String actor, boolean owner);

    MarketingContentResponse approveContent(UUID contentId, MarketingApprovalRequest request, String actor);

    MarketingContentResponse rejectContent(UUID contentId, MarketingRejectRequest request, String actor);

    MarketingContentResponse scheduleContent(UUID contentId, MarketingScheduleRequest request, String actor);

    MarketingContentResponse publishNow(UUID contentId, String actor);

    List<MarketingApprovalQueueItemResponse> getApprovalQueue();

    MarketingAnalyticsResponse getAnalytics(UUID campaignId, MarketingPlatform platform, LocalDate fromDate, LocalDate toDate);

    void publishScheduled();
}
