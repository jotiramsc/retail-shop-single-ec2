package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class OrderItemResponse {
    private UUID productId;
    private String productName;
    private String sku;
    private String category;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal lineTotal;
}
