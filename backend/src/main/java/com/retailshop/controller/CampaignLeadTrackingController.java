package com.retailshop.controller;

import com.retailshop.dto.CampaignLeadVisitRequest;
import com.retailshop.dto.CampaignLeadVisitResponse;
import com.retailshop.service.CampaignLeadTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaign-leads")
@RequiredArgsConstructor
public class CampaignLeadTrackingController {

    private final CampaignLeadTrackingService campaignLeadTrackingService;

    @PostMapping("/visits")
    public CampaignLeadVisitResponse recordVisit(@Valid @RequestBody CampaignLeadVisitRequest request) {
        return campaignLeadTrackingService.recordVisit(request);
    }
}
