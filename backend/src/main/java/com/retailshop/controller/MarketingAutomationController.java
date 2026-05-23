package com.retailshop.controller;

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
import com.retailshop.service.MarketingAutomationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
@Slf4j
public class MarketingAutomationController {

    private final MarketingAutomationService marketingAutomationService;
    private final TaskExecutor applicationTaskExecutor;

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    public MarketingCampaignResponse createCampaign(@Valid @RequestBody MarketingCampaignRequest request) {
        requireMarketingAccess();
        return marketingAutomationService.createCampaign(request, currentUsername());
    }

    @PostMapping("/campaigns/{campaignId}/generate")
    public MarketingCampaignResponse generateCampaign(@PathVariable UUID campaignId) {
        requireMarketingAccess();
        return marketingAutomationService.generateCampaign(campaignId, currentUsername());
    }

    @PostMapping("/campaigns/{campaignId}/generate-async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MarketingCampaignResponse generateCampaignAsync(@PathVariable UUID campaignId) {
        requireMarketingAccess();
        String actor = currentUsername();
        MarketingCampaignResponse response = marketingAutomationService.startCampaignGeneration(campaignId);
        applicationTaskExecutor.execute(() -> {
            try {
                marketingAutomationService.generateCampaign(campaignId, actor);
            } catch (Exception exception) {
                log.error("AI campaign generation failed for campaign {}", campaignId, exception);
                marketingAutomationService.markCampaignGenerationFailed(campaignId);
            }
        });
        return response;
    }

    @GetMapping("/campaigns")
    public PaginatedResponse<MarketingCampaignListItemResponse> listCampaigns(
            @RequestParam(required = false) MarketingWorkflowStatus status,
            @RequestParam(required = false) MarketingPlatform platform,
            @RequestParam(required = false) MarketingCampaignType type,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        requireMarketingAccess();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return marketingAutomationService.getCampaigns(status, platform, type, fromDate, toDate, pageable);
    }

    @GetMapping("/campaigns/{campaignId}")
    public MarketingCampaignResponse getCampaign(@PathVariable UUID campaignId) {
        requireMarketingAccess();
        return marketingAutomationService.getCampaign(campaignId);
    }

    @DeleteMapping("/campaigns/{campaignId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCampaign(@PathVariable UUID campaignId) {
        requireAdminOrOwner();
        marketingAutomationService.deleteCampaign(campaignId);
    }

    @GetMapping("/suggestions")
    public List<MarketingCampaignSuggestionResponse> campaignSuggestions(
            @RequestParam(defaultValue = "20") int daysAhead
    ) {
        requireMarketingAccess();
        return marketingAutomationService.getCampaignSuggestions(daysAhead);
    }

    @PutMapping("/content/{contentId}")
    public MarketingContentResponse updateContent(@PathVariable UUID contentId,
                                                  @Valid @RequestBody MarketingContentUpdateRequest request) {
        requireMarketingAccess();
        return marketingAutomationService.updateContent(contentId, request, currentUsername(), isOwner());
    }

    @PostMapping("/content/{contentId}/approve")
    public MarketingContentResponse approveContent(@PathVariable UUID contentId,
                                                   @RequestBody(required = false) MarketingApprovalRequest request) {
        requireAdminOrOwner();
        return marketingAutomationService.approveContent(contentId, request == null ? new MarketingApprovalRequest() : request, currentUsername());
    }

    @PostMapping("/content/{contentId}/reject")
    public MarketingContentResponse rejectContent(@PathVariable UUID contentId,
                                                  @Valid @RequestBody MarketingRejectRequest request) {
        requireAdminOrOwner();
        return marketingAutomationService.rejectContent(contentId, request, currentUsername());
    }

    @PostMapping("/content/{contentId}/schedule")
    public MarketingContentResponse scheduleContent(@PathVariable UUID contentId,
                                                    @Valid @RequestBody MarketingScheduleRequest request) {
        requireMarketingAccess();
        return marketingAutomationService.scheduleContent(contentId, request, currentUsername());
    }

    @PostMapping("/content/{contentId}/publish-now")
    public MarketingContentResponse publishNow(@PathVariable UUID contentId) {
        requireMarketingAccess();
        return marketingAutomationService.publishNow(contentId, currentUsername());
    }

    @GetMapping("/approval-queue")
    public List<MarketingApprovalQueueItemResponse> approvalQueue() {
        requireMarketingAccess();
        return marketingAutomationService.getApprovalQueue();
    }

    @GetMapping("/analytics")
    public MarketingAnalyticsResponse analytics(@RequestParam(required = false) UUID campaignId,
                                                @RequestParam(required = false) MarketingPlatform platform,
                                                @RequestParam(required = false) LocalDate fromDate,
                                                @RequestParam(required = false) LocalDate toDate) {
        requireMarketingAccess();
        return marketingAutomationService.getAnalytics(campaignId, platform, fromDate, toDate);
    }

    private void requireMarketingAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().stream().noneMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_OWNER".equals(authority.getAuthority())
                        || "PERM_CAMPAIGNS".equals(authority.getAuthority())
                        || "PERM_CAMPAIGNS_DASHBOARD".equals(authority.getAuthority())
                        || "PERM_CAMPAIGNS_LIST".equals(authority.getAuthority())
                        || "PERM_CAMPAIGNS_CREATE".equals(authority.getAuthority())
                        || "PERM_CAMPAIGNS_TEMPLATES".equals(authority.getAuthority())
                        || "PERM_CAMPAIGNS_AUDIENCE".equals(authority.getAuthority())
                        || "PERM_CAMPAIGNS_OFFERS".equals(authority.getAuthority())
                        || "PERM_CAMPAIGNS_APPROVAL".equals(authority.getAuthority())
                        || "PERM_OFFERS".equals(authority.getAuthority())
                        || "PERM_MARKETING_AUTOMATION".equals(authority.getAuthority()))) {
            throw new org.springframework.security.access.AccessDeniedException("Marketing access is required");
        }
    }

    private void requireAdminOrOwner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().stream().noneMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_OWNER".equals(authority.getAuthority()))) {
            throw new org.springframework.security.access.AccessDeniedException("Owner or admin access is required");
        }
    }

    private boolean isOwner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_OWNER".equals(authority.getAuthority()));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
}
