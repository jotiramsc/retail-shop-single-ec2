package com.retailshop.controller;

import com.retailshop.dto.CustomerActivityTrackRequest;
import com.retailshop.dto.CustomerLocationUpdateRequest;
import com.retailshop.security.CustomerSecurity;
import com.retailshop.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerTrackingController {

    private final CustomerService customerService;

    @PostMapping("/activity/track")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Map<String, Boolean> trackActivity(@RequestBody(required = false) CustomerActivityTrackRequest request) {
        customerService.recordActivity(CustomerSecurity.currentCustomerId(), request);
        return Map.of("saved", true);
    }

    @PostMapping("/location/update")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Map<String, Boolean> updateLocation(@RequestBody(required = false) CustomerLocationUpdateRequest request) {
        customerService.recordLocation(CustomerSecurity.currentCustomerId(), request);
        return Map.of("saved", true);
    }
}
