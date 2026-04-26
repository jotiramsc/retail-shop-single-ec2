package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ProductCategoryOptionResponse {
    private UUID id;
    private String code;
    private String displayName;
    private Boolean active;
    private LocalDateTime createdAt;
}
