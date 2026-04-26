package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class AddressResponse {
    private UUID id;
    private String label;
    private String recipientName;
    private String mobile;
    private String line1;
    private String line2;
    private String landmark;
    private String city;
    private String state;
    private String pincode;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
