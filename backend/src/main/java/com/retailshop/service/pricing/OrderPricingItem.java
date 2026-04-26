package com.retailshop.service.pricing;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class OrderPricingItem {
    private UUID productId;
    private String productName;
    private String sku;
    private String category;
    private String imageDataUrl;
    private BigDecimal unitPrice;
    private int quantity;
    private int stockAvailable;
    private BigDecimal lineTotal;
    private BigDecimal automaticDiscount;
    private UUID appliedOfferId;
    private String appliedOfferName;
}
