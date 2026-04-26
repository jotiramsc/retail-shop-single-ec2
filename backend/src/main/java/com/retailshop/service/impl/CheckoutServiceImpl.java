package com.retailshop.service.impl;

import com.retailshop.dto.CartItemResponse;
import com.retailshop.dto.CartResponse;
import com.retailshop.dto.CheckoutQuoteResponse;
import com.retailshop.service.CheckoutService;
import com.retailshop.service.pricing.OrderPricingItem;
import com.retailshop.service.pricing.OrderPricingResult;
import com.retailshop.service.pricing.OrderPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    private final OrderPricingService orderPricingService;

    @Override
    @Transactional(readOnly = true)
    public CheckoutQuoteResponse quote(UUID customerId, String couponCode) {
        OrderPricingResult pricing = orderPricingService.priceCart(customerId, couponCode);
        return CheckoutQuoteResponse.builder()
                .cart(CartResponse.builder()
                        .items(mapItems(pricing.getItems()))
                        .subtotal(pricing.getSubtotal())
                        .build())
                .applicableOffers(pricing.getApplicableOffers())
                .appliedCouponCode(pricing.getAppliedCouponCode())
                .subtotal(pricing.getSubtotal())
                .automaticDiscount(pricing.getAutomaticDiscount())
                .couponDiscount(pricing.getCouponDiscount())
                .discount(pricing.getDiscount())
                .tax(pricing.getTax())
                .delivery(pricing.getDelivery())
                .finalTotal(pricing.getFinalTotal())
                .build();
    }

    private List<CartItemResponse> mapItems(List<OrderPricingItem> items) {
        return items.stream()
                .map(item -> CartItemResponse.builder()
                        .productId(item.getProductId())
                        .name(item.getProductName())
                        .sku(item.getSku())
                        .category(item.getCategory())
                        .imageDataUrl(item.getImageDataUrl())
                        .price(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .stockAvailable(item.getStockAvailable())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();
    }
}
