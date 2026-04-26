package com.retailshop.service;

import com.retailshop.dto.CartItemRequest;
import com.retailshop.dto.CartMergeRequest;
import com.retailshop.dto.CartResponse;

import java.util.UUID;

public interface CartService {
    CartResponse getCart(UUID customerId);

    CartResponse addItem(UUID customerId, CartItemRequest request);

    CartResponse updateItem(UUID customerId, CartItemRequest request);

    CartResponse removeItem(UUID customerId, UUID productId);

    CartResponse mergeGuestCart(UUID customerId, CartMergeRequest request);

    void clearCart(UUID customerId);
}
