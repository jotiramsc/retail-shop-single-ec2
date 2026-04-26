package com.retailshop.dto;

import com.retailshop.enums.OrderSource;
import com.retailshop.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ReportOrderRowResponse {
    private UUID id;
    private String referenceNumber;
    private OrderSource source;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private String customerName;
    private String customerMobile;
    private String paymentMode;
    private String paymentStatus;
    private BigDecimal finalAmount;
    private BigDecimal discount;
    private String couponCode;
}
