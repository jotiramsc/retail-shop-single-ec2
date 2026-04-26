package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaymentOrderResponse {
    private String provider;
    private boolean configured;
    private String keyId;
    private String orderId;
    private String receipt;
    private String currency;
    private BigDecimal amount;
    private Long amountSubunits;
    private String paymentUrl;
}
