package com.retailshop.service;

import com.retailshop.dto.CampaignLogResponse;
import com.retailshop.dto.CampaignRequest;

import java.util.List;

public interface CampaignService {
    List<CampaignLogResponse> sendCampaign(CampaignRequest request);
    List<CampaignLogResponse> getHistory();
}
