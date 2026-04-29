package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SiteInteractionRecentVisitResponse {
    private LocalDateTime createdAt;
    private String landingPath;
    private String sourceType;
    private String sourceLabel;
    private String ipAddress;
    private String city;
    private String region;
    private String countryName;
    private String countryCode;
    private String timezone;
    private String exactLocationName;
    private String postalCode;
    private String locationSource;
    private Double latitude;
    private Double longitude;
    private Double locationAccuracyMeters;
    private String organization;
    private String referrerHost;
    private String utmSource;
    private String utmCampaign;
    private String acceptLanguage;
}
