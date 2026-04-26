package com.retailshop.controller;

import com.retailshop.dto.CampaignLogResponse;
import com.retailshop.dto.CampaignRequest;
import com.retailshop.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/campaign")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CampaignController {

    private final CampaignService campaignService;

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CampaignLogResponse> sendCampaign(@Valid @RequestBody CampaignRequest request) {
        return campaignService.sendCampaign(request);
    }

    @GetMapping("/history")
    public List<CampaignLogResponse> getHistory() {
        return campaignService.getHistory();
    }
}
