package com.retailshop.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "website_price_percentage", precision = 7, scale = 2)
    private BigDecimal websitePricePercentage;

    @Column(name = "website_price", precision = 12, scale = 2)
    private BigDecimal websitePrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "low_stock_threshold", nullable = false)
    private Integer lowStockThreshold;

    @Column(name = "image_data_url")
    private String imageDataUrl;

    @Column(name = "show_in_editors_picks", nullable = false)
    private Boolean showInEditorsPicks;

    @Column(name = "show_in_new_release", nullable = false)
    private Boolean showInNewRelease;

    @Column(name = "show_in_customer_access", nullable = false)
    private Boolean showInCustomerAccess;

    @Column(name = "show_in_shop_collection", nullable = false)
    private Boolean showInShopCollection;

    @Column(name = "show_in_featured_pieces", nullable = false)
    private Boolean showInFeaturedPieces;

    @Column(name = "show_in_story", nullable = false)
    private Boolean showInStory;

    @Column(name = "show_in_curated_selections", nullable = false)
    private Boolean showInCuratedSelections;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        normalizePricing();
        if (showInEditorsPicks == null) {
            showInEditorsPicks = Boolean.FALSE;
        }
        if (showInNewRelease == null) {
            showInNewRelease = Boolean.FALSE;
        }
        if (showInCustomerAccess == null) {
            showInCustomerAccess = Boolean.FALSE;
        }
        if (showInShopCollection == null) {
            showInShopCollection = Boolean.FALSE;
        }
        if (showInFeaturedPieces == null) {
            showInFeaturedPieces = Boolean.FALSE;
        }
        if (showInStory == null) {
            showInStory = Boolean.FALSE;
        }
        if (showInCuratedSelections == null) {
            showInCuratedSelections = Boolean.FALSE;
        }
    }

    @jakarta.persistence.PreUpdate
    public void preUpdate() {
        normalizePricing();
    }

    public BigDecimal getResolvedWebsitePrice() {
        if (sellingPrice == null) {
            return websitePrice == null ? null : websitePrice.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal normalizedShopPrice = sellingPrice.setScale(2, RoundingMode.HALF_UP);
        if (websitePricePercentage == null || websitePricePercentage.compareTo(BigDecimal.ZERO) <= 0) {
            return normalizedShopPrice;
        }

        return normalizedShopPrice
                .add(normalizedShopPrice.multiply(websitePricePercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void normalizePricing() {
        if (costPrice != null) {
            costPrice = costPrice.setScale(2, RoundingMode.HALF_UP);
        }
        if (sellingPrice != null) {
            sellingPrice = sellingPrice.setScale(2, RoundingMode.HALF_UP);
        }
        if (websitePricePercentage != null) {
            websitePricePercentage = websitePricePercentage.setScale(2, RoundingMode.HALF_UP);
            if (websitePricePercentage.compareTo(BigDecimal.ZERO) <= 0) {
                websitePricePercentage = null;
            }
        }
        websitePrice = getResolvedWebsitePrice();
    }
}
