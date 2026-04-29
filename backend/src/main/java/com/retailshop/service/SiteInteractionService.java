package com.retailshop.service;

import com.retailshop.dto.SiteInteractionReportResponse;
import com.retailshop.dto.SiteInteractionSummaryResponse;
import com.retailshop.dto.SiteVisitRequest;

public interface SiteInteractionService {
    SiteInteractionSummaryResponse recordVisit(SiteVisitRequest request, String userAgent, String acceptLanguage, String clientIpAddress);
    SiteInteractionReportResponse getReport(int days);
}
