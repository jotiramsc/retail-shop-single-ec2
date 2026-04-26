package com.retailshop.dto;

import com.retailshop.enums.OrderStatus;
import com.retailshop.enums.OrderSource;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class OrderResponse {
    private UUID id;
    private String orderNumber;
    private OrderSource source;
    private OrderStatus status;
    private String paymentStatus;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal tax;
    private BigDecimal delivery;
    private BigDecimal finalAmount;
    private String couponCode;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
}
