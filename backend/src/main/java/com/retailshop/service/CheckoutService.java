package com.retailshop.service;

import com.retailshop.dto.CheckoutQuoteResponse;

import java.util.UUID;

public interface CheckoutService {
    CheckoutQuoteResponse quote(UUID customerId, String couponCode);
}
