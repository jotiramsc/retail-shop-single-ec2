package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class CheckoutQuoteResponse {
    private CartResponse cart;
    private List<OfferResponse> applicableOffers;
    private String appliedCouponCode;
    private BigDecimal subtotal;
    private BigDecimal automaticDiscount;
    private BigDecimal couponDiscount;
    private BigDecimal discount;
    private BigDecimal tax;
    private BigDecimal delivery;
    private BigDecimal finalTotal;
}
