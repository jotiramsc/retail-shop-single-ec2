package com.retailshop.dto;
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
    private String category;
    private String sku;
    private BigDecimal sellingPrice;
    private Integer quantity;
    private Boolean inStock;
    private String stockLabel;
    private String imageDataUrl;
    private Boolean showInEditorsPicks;
    private Boolean showInNewRelease;
    private Boolean showInCustomerAccess;
    private Boolean showInShopCollection;
    private Boolean showInFeaturedPieces;
    private Boolean showInStory;
    private Boolean showInCuratedSelections;
    private LocalDateTime createdAt;
}
