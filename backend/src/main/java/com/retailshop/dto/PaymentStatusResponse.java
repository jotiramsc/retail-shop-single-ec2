package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentStatusResponse {
    private String provider;
    private String merchantOrderId;
    private String transactionId;
    private String state;
    private String paymentState;
    private String paymentMode;
    private boolean success;
}
