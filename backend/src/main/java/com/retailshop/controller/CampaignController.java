package com.retailshop.controller;

import com.retailshop.dto.CampaignLogResponse;
import com.retailshop.dto.CampaignRequest;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaign")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_CAMPAIGNS')")
public class CampaignController {

    private final CampaignService campaignService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<CampaignLogResponse> createCampaign(@Valid @RequestBody CampaignRequest request) {
        return campaignService.createCampaign(request, currentUsername());
    }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CampaignLogResponse> sendCampaign(@Valid @RequestBody CampaignRequest request) {
        return campaignService.createCampaign(request, currentUsername());
    }

    @PostMapping("/{campaignId}/publish")
    public List<CampaignLogResponse> publishCampaign(@PathVariable UUID campaignId) {
        return campaignService.publishCampaign(campaignId, currentUsername());
    }

    @PostMapping("/history/{campaignLogId}/retry")
    public CampaignLogResponse retryCampaignLog(@PathVariable UUID campaignLogId) {
        return campaignService.retryCampaignLog(campaignLogId, currentUsername());
    }

    @GetMapping("/history")
    public PaginatedResponse<CampaignLogResponse> getHistory(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return campaignService.getHistory(pageable);
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "system";
    }
}
