package com.retailshop.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class OmnichannelProductSearchRequest {
    private UUID leadId;
    private String channel;
    private String query;
    private String category;
    private String occasion;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Boolean inStockOnly = true;
    private String source;
    private String campaign;
    private String couponCode;
    private Integer limit;
}
