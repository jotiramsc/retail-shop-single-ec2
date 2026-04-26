package com.retailshop.dto;

import com.retailshop.enums.ProductCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ProductRequest {

    @NotBlank
    private String name;

    @NotNull
    private ProductCategory category;

    @NotBlank
    private String sku;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal costPrice;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal sellingPrice;

    @NotNull
    @Min(0)
    private Integer quantity;

    @NotNull
    @Min(0)
    private Integer lowStockThreshold;

    private String imageDataUrl;

    private Boolean showInEditorsPicks;

    private Boolean showInNewRelease;

    private Boolean showInCustomerAccess;

    private LocalDate expiryDate;
}
