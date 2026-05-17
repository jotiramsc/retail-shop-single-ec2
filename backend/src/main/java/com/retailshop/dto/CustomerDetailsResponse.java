package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CustomerDetailsResponse {
    private UUID id;
    private String name;
    private String mobile;
    private String email;
    private String fullAddress;
    private long totalOrders;
    private long pendingOrders;
    private BigDecimal totalSpent;
    private List<OrderSummary> orderHistory;

    @Getter
    @Builder
    public static class OrderSummary {
        private UUID id;
        private String orderNumber;
        private LocalDateTime createdAt;
        private BigDecimal amount;
        private String status;
    }
}
