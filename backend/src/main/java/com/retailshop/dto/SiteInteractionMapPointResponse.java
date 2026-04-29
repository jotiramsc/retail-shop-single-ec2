package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SiteInteractionMapPointResponse {
    private Double latitude;
    private Double longitude;
    private String locationName;
    private String postalCode;
    private String countryName;
    private String sourceType;
    private String sourceLabel;
    private long visits;
    private LocalDateTime latestVisitAt;
}
