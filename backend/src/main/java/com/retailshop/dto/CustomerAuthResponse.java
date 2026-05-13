package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CustomerAuthResponse {
    private UUID customerId;
    private String name;
    private String email;
    private String mobile;
    private String authProvider;
    private boolean mobileVerified;
    private boolean emailVerified;
    private boolean profileComplete;
    private boolean requiresMobileOtp;
    private List<String> missingFields;
    private String token;
}
