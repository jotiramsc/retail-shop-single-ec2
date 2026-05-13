package com.retailshop.dto;

import com.retailshop.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderStatusUpdateRequest {
    @NotNull
    private OrderStatus status;

    private String trackingId;
    private String trackingUrl;
    private String refundAmount;
}
