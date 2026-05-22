package com.retailshop.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CustomerActivityTrackRequest {
    private String activityType;
    private String searchKeyword;
    private String category;
    private String selectedCategory;
    private String filterUsed;
    private String priceRange;
    private UUID productId;
    private String productName;
    private Integer resultCount;
    private String clickedProduct;
    private Integer timeSpentSeconds;
    private String campaignSource;
    private String page;
    private String sourcePage;
}
