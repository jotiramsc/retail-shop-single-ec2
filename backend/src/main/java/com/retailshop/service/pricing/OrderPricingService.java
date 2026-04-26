package com.retailshop.service.pricing;

import java.util.Map;
import java.util.UUID;

public interface OrderPricingService {
    OrderPricingResult priceCart(UUID customerId, String couponCode);

    OrderPricingResult priceProducts(Map<UUID, Integer> normalizedItems, String couponCode);
}
