package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class OmnichannelProductCardResponse {
    private UUID productId;
    private String name;
    private String category;
    private String sku;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private BigDecimal offerPrice;
    private BigDecimal discountPercent;
    private Integer quantity;
    private Boolean inStock;
    private String stockLabel;
    private String imageUrl;
    private String shortBenefit;
    private String aiDescription;
    private String productUrl;
    private String buyNowUrl;
    private String checkoutUrl;
}
