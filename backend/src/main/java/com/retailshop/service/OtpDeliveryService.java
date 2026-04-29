package com.retailshop.service;

public interface OtpDeliveryService {
    boolean isConfigured();

    MarketingChannelResult sendOtp(String mobile, String otp, long otpTtlMinutes);

    String getChannel();
}
