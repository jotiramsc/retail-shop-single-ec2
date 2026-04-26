package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class DailyReportResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private Long invoiceCount;
    private BigDecimal totalSales;
    private BigDecimal totalDiscount;
}
