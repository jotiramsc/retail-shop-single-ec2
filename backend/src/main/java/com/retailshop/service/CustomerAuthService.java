package com.retailshop.service;

import com.retailshop.dto.CustomerAuthResponse;
import com.retailshop.dto.CustomerGoogleLoginRequest;
import com.retailshop.dto.CustomerOtpRequest;
import com.retailshop.dto.CustomerOtpSendResponse;
import com.retailshop.dto.CustomerOtpVerifyRequest;

import java.util.UUID;

public interface CustomerAuthService {
    CustomerOtpSendResponse sendOtp(CustomerOtpRequest request);

    CustomerAuthResponse verifyOtp(CustomerOtpVerifyRequest request);

    CustomerAuthResponse verifyOtpForCustomer(UUID customerId, CustomerOtpVerifyRequest request);

    CustomerAuthResponse verifyGoogleMobileOtp(CustomerOtpVerifyRequest request);

    CustomerAuthResponse loginWithGoogle(CustomerGoogleLoginRequest request);
}
