package com.retailshop.dto;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class OfferSuggestionResponse {
    private UUID productId;
    private String productName;
    private String category;
    private Integer currentQuantity;
    private BigDecimal suggestedDiscountPercent;
    private String reason;
}
