package com.retailshop.dto;

import com.retailshop.enums.ProductCategory;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class LowStockProductResponse {
    private UUID productId;
    private String productName;
    private ProductCategory category;
    private String sku;
    private Integer quantity;
    private Integer threshold;
}
