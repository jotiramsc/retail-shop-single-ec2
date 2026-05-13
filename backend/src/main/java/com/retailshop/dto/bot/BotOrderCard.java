package com.retailshop.dto.bot;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class BotOrderCard {
    private String orderId;
    private String orderNumber;
    private LocalDateTime createdAt;
    private BigDecimal finalAmount;
    private String paymentStatus;
    private String orderStatus;
    private List<String> itemImages;
    private int itemCount;
    private List<String> actions;
}
