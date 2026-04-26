package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class PurchaseHistoryResponse {
    private UUID invoiceId;
    private String invoiceNumber;
    private BigDecimal finalAmount;
    private LocalDateTime createdAt;
}
