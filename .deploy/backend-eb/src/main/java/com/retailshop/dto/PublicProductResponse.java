package com.retailshop.dto;

import com.retailshop.enums.ProductCategory;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class PublicProductResponse {
    private UUID id;
    private String name;
    private ProductCategory category;
    private String sku;
    private BigDecimal sellingPrice;
    private String imageDataUrl;
    private Boolean showInEditorsPicks;
    private Boolean showInNewRelease;
    private Boolean showInCustomerAccess;
    private LocalDateTime createdAt;
}
