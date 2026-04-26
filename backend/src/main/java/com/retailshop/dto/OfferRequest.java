package com.retailshop.dto;

import com.retailshop.enums.OfferType;
import com.retailshop.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class OfferRequest {

    @NotBlank
    private String name;

    @NotNull
    private OfferType type;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal value;

    private String category;

    private UUID productId;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    private Boolean active;

    private String couponCode;

    private DiscountType discountType;

    @DecimalMin("0.0")
    private BigDecimal discountValue;

    @DecimalMin("0.0")
    private BigDecimal maxDiscountAmount;

    @DecimalMin("0.0")
    private BigDecimal minOrderValue;

    private String applicableOn;

    private LocalDate validFrom;

    private LocalDate validTo;
}
