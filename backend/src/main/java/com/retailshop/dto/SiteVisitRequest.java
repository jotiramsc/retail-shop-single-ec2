package com.retailshop.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SiteVisitRequest {
    private String visitorId;
    private String path;
    private String referrer;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;
    private String timezone;
    private Double latitude;
    private Double longitude;
    private Double accuracyMeters;
}
