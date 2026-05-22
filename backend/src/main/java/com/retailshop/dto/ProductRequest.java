package com.retailshop.dto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class ProductRequest {

    @NotBlank
    private String name;

    @NotNull
    private String category;

    @NotBlank
    private String sku;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal costPrice;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal sellingPrice;

    @DecimalMin("0.0")
    private BigDecimal websitePricePercentage;

    @NotNull
    @Min(0)
    private Integer quantity;

    @NotNull
    @Min(0)
    private Integer lowStockThreshold;

    private String imageDataUrl;

    private List<String> productImages;

    private String description;

    private Boolean generateAiDescription;

    private Boolean showOnWebsite;

    private Boolean useForBilling;

    private Boolean showInEditorsPicks;

    private Boolean showInNewRelease;

    private Boolean showInCustomerAccess;

    private Boolean showInShopCollection;

    private Boolean showInFeaturedPieces;

    private Boolean showInStory;

    private Boolean showInCuratedSelections;

    private Boolean facebookSyncEnabled;

    private LocalDate expiryDate;
}
