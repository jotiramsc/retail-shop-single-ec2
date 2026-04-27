package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class SalesReportResponse {
    private String period;
    private String reportLabel;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String scope;
    private String category;
    private UUID productId;
    private long orderCount;
    private long quantitySold;
    private BigDecimal grossSales;
    private BigDecimal discount;
    private BigDecimal netSales;
    private List<SalesReportRowResponse> rows;
}
