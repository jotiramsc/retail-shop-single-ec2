package com.retailshop.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentOrderRequest {
    private String couponCode;
    private String redirectUrl;
}
