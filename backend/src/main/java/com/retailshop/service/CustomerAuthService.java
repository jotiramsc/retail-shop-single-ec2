package com.retailshop.service;

import com.retailshop.dto.CustomerAuthResponse;
import com.retailshop.dto.CustomerOtpRequest;
import com.retailshop.dto.CustomerOtpSendResponse;
import com.retailshop.dto.CustomerOtpVerifyRequest;

public interface CustomerAuthService {
    CustomerOtpSendResponse sendOtp(CustomerOtpRequest request);

    CustomerAuthResponse verifyOtp(CustomerOtpVerifyRequest request);
}
