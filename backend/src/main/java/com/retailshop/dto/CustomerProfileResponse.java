package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CustomerProfileResponse {
    private UUID customerId;
    private String name;
    private String email;
    private String mobile;
    private LocalDate dateOfBirth;
    private String gender;
    private String profileImageUrl;
    private String alternateMobile;
    private String authProvider;
    private boolean mobileVerified;
    private boolean emailVerified;
    private boolean profileComplete;
    private List<String> missingFields;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime profileCompletedAt;
}
