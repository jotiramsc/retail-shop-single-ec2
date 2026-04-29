package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class SiteInteractionReportResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private long totalVisits;
    private long visitsInRange;
    private long directVisits;
    private long searchVisits;
    private long socialVisits;
    private long referralVisits;
    private long campaignVisits;
    private List<SiteInteractionDailyResponse> dailyVisits;
    private List<SiteInteractionSourceResponse> sourceBreakdown;
    private List<SiteInteractionCountryResponse> topCountries;
    private List<SiteInteractionLabelCountResponse> topReferrers;
    private List<SiteInteractionLabelCountResponse> topLandingPages;
    private List<SiteInteractionMapPointResponse> mapPoints;
    private List<SiteInteractionRecentVisitResponse> recentVisits;
}
