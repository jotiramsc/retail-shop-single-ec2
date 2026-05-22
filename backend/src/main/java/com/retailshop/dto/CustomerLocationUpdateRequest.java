package com.retailshop.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerLocationUpdateRequest {
    private Double latitude;
    private Double longitude;
    private String city;
    private String state;
    private String country;
    private String pincode;
    private Double accuracyMeters;
    private String locationSource;
}
