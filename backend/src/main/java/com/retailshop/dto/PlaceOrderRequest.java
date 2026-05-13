package com.retailshop.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PlaceOrderRequest {
    @NotNull
    private UUID addressId;

    private String couponCode;
    private String paymentProvider;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;
}
