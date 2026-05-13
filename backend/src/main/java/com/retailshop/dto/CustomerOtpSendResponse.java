package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerOtpSendResponse {
    private boolean externalProviderConfigured;
    private boolean otpRequired;
    private boolean customerExists;
    private String message;
    private String channel;
    private String maskedMobile;
    private String nextStep;
    private long resendCooldownSeconds;
    private long expiresInSeconds;
}
