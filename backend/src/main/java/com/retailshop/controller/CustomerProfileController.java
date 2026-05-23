package com.retailshop.controller;

import com.retailshop.dto.CustomerAuthResponse;
import com.retailshop.dto.CustomerOtpVerifyRequest;
import com.retailshop.dto.CustomerProfileRequest;
import com.retailshop.dto.CustomerProfileResponse;
import com.retailshop.security.CustomerSecurity;
import com.retailshop.service.CustomerAuthService;
import com.retailshop.service.CustomerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/customer-profile")
@RequiredArgsConstructor
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;
    private final CustomerAuthService customerAuthService;

    @GetMapping
    public CustomerProfileResponse getProfile() {
        return customerProfileService.getProfile(CustomerSecurity.currentCustomerId());
    }

    @PutMapping
    public CustomerProfileResponse updateProfile(@Valid @RequestBody CustomerProfileRequest request) {
        return customerProfileService.updateProfile(CustomerSecurity.currentCustomerId(), request);
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CustomerProfileResponse updateProfileImage(@RequestParam("image") MultipartFile image) {
        return customerProfileService.updateProfileImage(CustomerSecurity.currentCustomerId(), image);
    }

    @PostMapping("/mobile/verify-otp")
    public CustomerAuthResponse verifyProfileMobileOtp(@Valid @RequestBody CustomerOtpVerifyRequest request) {
        return customerAuthService.verifyOtpForCustomer(CustomerSecurity.currentCustomerId(), request);
    }
}
