package com.retailshop.dto.bot;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class BotPaymentSummary {
    private String orderNumber;
    private String paymentMethod;
    private String paymentStatus;
    private BigDecimal amount;
    private String transactionId;
    private String nextAction;
}
