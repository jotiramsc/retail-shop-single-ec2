package com.retailshop.dto;

import com.retailshop.enums.PaymentMode;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class InvoiceResponse {
    private UUID id;
    private String invoiceNumber;
    private UUID customerId;
    private String customerName;
    private String customerMobile;
    private BigDecimal totalAmount;
    private BigDecimal discount;
    private BigDecimal finalAmount;
    private PaymentMode paymentMode;
    private String couponCode;
    private LocalDateTime createdAt;
    private List<InvoiceItemResponse> items;
}
