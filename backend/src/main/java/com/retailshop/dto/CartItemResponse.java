package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class CartItemResponse {
    private UUID productId;
    private String name;
    private String sku;
    private String category;
    private String imageDataUrl;
    private BigDecimal price;
    private Integer quantity;
    private Integer stockAvailable;
    private BigDecimal lineTotal;
}
