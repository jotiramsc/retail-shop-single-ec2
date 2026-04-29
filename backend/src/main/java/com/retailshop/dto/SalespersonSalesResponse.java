package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class SalespersonSalesResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private String viewType;
    private boolean lockedToCurrentUser;
    private String salespersonId;
    private String salespersonName;
    private BigDecimal totalSalesAmount;
    private long totalOrders;
    private long totalItemsSold;
    private BigDecimal averageOrderValue;
    private List<SalespersonSalesTrendRowResponse> trend;
    private List<SalespersonSalesRecordResponse> records;
}
