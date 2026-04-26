package com.retailshop.service;

import com.retailshop.dto.OrderResponse;
import com.retailshop.dto.PlaceOrderRequest;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    OrderResponse placeOrder(UUID customerId, PlaceOrderRequest request);

    List<OrderResponse> getOrders(UUID customerId);
}
