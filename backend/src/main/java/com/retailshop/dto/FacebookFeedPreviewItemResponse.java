package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class FacebookFeedPreviewItemResponse {
    private String productId;
    private String productName;
    private String category;
    private BigDecimal price;
    private BigDecimal salePrice;
    private String status;
    private String issue;
}
