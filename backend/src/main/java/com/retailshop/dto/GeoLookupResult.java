package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GeoLookupResult {
    private String ipAddress;
    private String city;
    private String region;
    private String countryName;
    private String countryCode;
    private String timezone;
    private Double latitude;
    private Double longitude;
    private String organization;
    private String locationLabel;
    private String postalCode;
}
