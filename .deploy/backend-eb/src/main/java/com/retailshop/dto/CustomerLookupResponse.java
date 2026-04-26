package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class CustomerLookupResponse {
    private UUID id;
    private String name;
    private String mobile;
    private long totalInvoices;
    private BigDecimal totalSpent;
    private LocalDateTime lastPurchaseAt;
}
