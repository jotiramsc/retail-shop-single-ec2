package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class SalesReportRowResponse {
    private UUID productId;
    private String productName;
    private String category;
    private String sku;
    private long quantitySold;
    private BigDecimal grossSales;
    private BigDecimal discount;
    private BigDecimal netSales;
}
