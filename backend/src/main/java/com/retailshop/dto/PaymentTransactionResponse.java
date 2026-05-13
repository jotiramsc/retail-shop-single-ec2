package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class PaymentTransactionResponse {
    private UUID id;
    private String provider;
    private String operation;
    private String status;
    private UUID customerId;
    private UUID orderId;
    private String orderNumber;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private String receipt;
    private String currency;
    private BigDecimal amount;
    private Long amountSubunits;
    private String paymentState;
    private String gatewayStatus;
    private String webhookEvent;
    private String signatureStatus;
    private String failureCode;
    private String errorMessage;
    private String requestPayload;
    private String responsePayload;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
