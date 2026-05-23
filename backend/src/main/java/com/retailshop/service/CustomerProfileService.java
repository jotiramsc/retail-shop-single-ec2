package com.retailshop.service;

import com.retailshop.dto.CustomerProfileRequest;
import com.retailshop.dto.CustomerProfileResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface CustomerProfileService {
    CustomerProfileResponse getProfile(UUID customerId);

    CustomerProfileResponse updateProfile(UUID customerId, CustomerProfileRequest request);

    CustomerProfileResponse updateProfileImage(UUID customerId, MultipartFile image);

    void ensureCheckoutReady(UUID customerId);
}
