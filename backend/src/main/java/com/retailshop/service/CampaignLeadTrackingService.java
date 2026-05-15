package com.retailshop.service;

import com.retailshop.dto.CampaignLeadVisitRequest;
import com.retailshop.dto.CampaignLeadVisitResponse;

public interface CampaignLeadTrackingService {
    CampaignLeadVisitResponse recordVisit(CampaignLeadVisitRequest request);
}
