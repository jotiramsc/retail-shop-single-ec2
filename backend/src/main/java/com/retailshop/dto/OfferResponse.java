package com.retailshop.dto;

import com.retailshop.enums.OfferType;
import com.retailshop.enums.DiscountType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class OfferResponse {
    private UUID id;
    private String name;
    private OfferType type;
    private BigDecimal value;
    private String category;
    private UUID productId;
    private String productName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean active;
    private String couponCode;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderValue;
    private String applicableOn;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String buyCategory;
    private UUID buyProductId;
    private String buyProductName;
    private Integer buyQuantity;
    private String getCategory;
    private UUID getProductId;
    private String getProductName;
    private Integer getQuantity;
    private String rewardMode;
    private BigDecimal rewardDiscountPercent;
    private String scheduleType;
    private List<String> specificDays;
}
