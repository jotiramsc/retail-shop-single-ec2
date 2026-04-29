package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiteInteractionCountryResponse {
    private String countryName;
    private String countryCode;
    private long visits;
}
