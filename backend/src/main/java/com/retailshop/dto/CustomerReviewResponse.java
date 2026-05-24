package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class CustomerReviewResponse {
    private UUID id;
    private String customerName;
    private String mobile;
    private String city;
    private String product;
    private Integer rating;
    private String comment;
    private Boolean approved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
