package com.retailshop.dto;

import com.retailshop.enums.ProductCategory;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ProductResponse {
    private UUID id;
    private String name;
    private ProductCategory category;
    private String sku;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private Integer quantity;
    private Integer lowStockThreshold;
    private String imageDataUrl;
    private Boolean showInEditorsPicks;
    private Boolean showInNewRelease;
    private Boolean showInCustomerAccess;
    private LocalDate expiryDate;
    private LocalDateTime createdAt;
}
