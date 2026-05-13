package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class WishlistItemResponse {
    private UUID productId;
    private String name;
    private String sku;
    private String category;
    private String imageDataUrl;
    private BigDecimal price;
    private Integer stockAvailable;
    private boolean inStock;
    private LocalDateTime createdAt;
}
