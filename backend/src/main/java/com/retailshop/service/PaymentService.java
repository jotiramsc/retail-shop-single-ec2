package com.retailshop.service;

import com.retailshop.dto.PaymentOrderResponse;
import com.retailshop.dto.PaymentStatusResponse;
import com.retailshop.dto.PlaceOrderRequest;

import java.math.BigDecimal;

public interface PaymentService {
    PaymentOrderResponse createPaymentOrder(BigDecimal amount, String receipt);

    PaymentStatusResponse getPaymentStatus(String merchantOrderId);

    boolean verifyPayment(PlaceOrderRequest request, BigDecimal amount);
}
