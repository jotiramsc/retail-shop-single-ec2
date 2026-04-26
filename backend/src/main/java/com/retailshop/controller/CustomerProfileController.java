package com.retailshop.controller;

import com.retailshop.dto.CustomerProfileRequest;
import com.retailshop.dto.CustomerProfileResponse;
import com.retailshop.security.CustomerSecurity;
import com.retailshop.service.CustomerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer-profile")
@RequiredArgsConstructor
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;

    @GetMapping
    public CustomerProfileResponse getProfile() {
        return customerProfileService.getProfile(CustomerSecurity.currentCustomerId());
    }

    @PutMapping
    public CustomerProfileResponse updateProfile(@Valid @RequestBody CustomerProfileRequest request) {
        return customerProfileService.updateProfile(CustomerSecurity.currentCustomerId(), request);
    }
}
