package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SalespersonSalesTrendRowResponse {
    private String label;
    private long orderCount;
    private long itemsSold;
    private BigDecimal totalSalesAmount;
}
