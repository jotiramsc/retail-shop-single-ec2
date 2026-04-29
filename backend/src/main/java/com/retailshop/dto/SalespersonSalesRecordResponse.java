package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class SalespersonSalesRecordResponse {
    private UUID id;
    private LocalDateTime date;
    private String billNo;
    private String customerName;
    private String salespersonName;
    private BigDecimal totalAmount;
    private String paymentMode;
}
