package com.retailshop.service;

import com.retailshop.dto.CampaignLogResponse;
import com.retailshop.dto.CampaignRequest;
import com.retailshop.dto.PaginatedResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CampaignService {
    List<CampaignLogResponse> createCampaign(CampaignRequest request, String publishedBy);
    List<CampaignLogResponse> publishCampaign(UUID campaignId, String publishedBy);
    CampaignLogResponse retryCampaignLog(UUID campaignLogId, String publishedBy);
    PaginatedResponse<CampaignLogResponse> getHistory(Pageable pageable);
}
