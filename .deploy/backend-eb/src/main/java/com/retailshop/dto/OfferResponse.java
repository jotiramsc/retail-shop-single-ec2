package com.retailshop.dto;

import com.retailshop.enums.OfferType;
import com.retailshop.enums.ProductCategory;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class OfferResponse {
    private UUID id;
    private String name;
    private OfferType type;
    private BigDecimal value;
    private ProductCategory category;
    private UUID productId;
    private String productName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean active;
}
