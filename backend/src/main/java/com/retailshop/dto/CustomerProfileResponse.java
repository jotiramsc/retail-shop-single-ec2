package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class CustomerProfileResponse {
    private UUID customerId;
    private String name;
    private String mobile;
    private LocalDateTime createdAt;
}
