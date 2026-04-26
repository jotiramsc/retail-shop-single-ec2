package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerOtpSendResponse {
    private boolean externalProviderConfigured;
    private String message;
    private String devOtp;
    private String channel;
    private String maskedMobile;
    private long resendCooldownSeconds;
    private long expiresInSeconds;
}
