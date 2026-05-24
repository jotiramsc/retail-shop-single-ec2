package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CustomerAuthResponse {
    private UUID customerId;
    private String name;
    private String email;
    private String mobile;
    private String profileImageUrl;
    private String authProvider;
    private boolean mobileVerified;
    private String verificationStatus;
    private boolean loginEnabled;
    private LocalDateTime otpVerifiedAt;
    private boolean emailVerified;
    private boolean profileComplete;
    private boolean requiresMobileOtp;
    private List<String> missingFields;
    private String token;
}
