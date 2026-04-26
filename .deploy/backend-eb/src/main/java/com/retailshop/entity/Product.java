package com.retailshop.entity;

import com.retailshop.enums.ProductCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductCategory category;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

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
        if (showInEditorsPicks == null) {
            showInEditorsPicks = Boolean.FALSE;
        }
        if (showInNewRelease == null) {
            showInNewRelease = Boolean.FALSE;
        }
        if (showInCustomerAccess == null) {
            showInCustomerAccess = Boolean.FALSE;
        }
    }
}
