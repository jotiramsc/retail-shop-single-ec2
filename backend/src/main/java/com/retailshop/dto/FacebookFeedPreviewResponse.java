package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class FacebookFeedPreviewResponse {
    private Boolean facebookCatalogEnabled;
    private Boolean pixelConfigured;
    private Integer syncedCategories;
    private Integer syncedProducts;
    private LocalDateTime lastFeedGeneratedAt;
    private List<FacebookFeedPreviewItemResponse> items;
}
