package com.retailshop.service.pricing;

import com.retailshop.dto.OfferResponse;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class OrderPricingResult {
    private List<OrderPricingItem> items;
    private List<OfferResponse> applicableOffers;
    private String requestedCouponCode;
    private String appliedCouponCode;
    private BigDecimal subtotal;
    private BigDecimal automaticDiscount;
    private BigDecimal couponDiscount;
    private BigDecimal discount;
    private BigDecimal tax;
    private BigDecimal delivery;
    private BigDecimal finalTotal;
}
