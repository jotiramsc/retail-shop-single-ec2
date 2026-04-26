package com.retailshop.service.impl;

import com.retailshop.dto.CartItemRequest;
import com.retailshop.dto.CartItemResponse;
import com.retailshop.dto.CartMergeRequest;
import com.retailshop.dto.CartResponse;
import com.retailshop.entity.Cart;
import com.retailshop.entity.CartItem;
import com.retailshop.entity.Customer;
import com.retailshop.entity.Product;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.CartRepository;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.CartService;
import com.retailshop.service.support.RedisStateStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final RedisStateStore redisStateStore;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(UUID customerId) {
        return redisStateStore.getCart(customerId)
                .orElseGet(() -> {
                    CartResponse response = cartRepository.findByCustomerId(customerId).map(this::mapCart).orElseGet(this::emptyCart);
                    redisStateStore.putCart(customerId, response);
                    return response;
                });
    }

    @Override
    @Transactional
    public CartResponse addItem(UUID customerId, CartItemRequest request) {
        Cart cart = getOrCreateCart(customerId);
        Product product = loadProduct(request.getProductId());
        validateStock(product, request.getQuantity());
        CartItem item = cart.getItems().stream()
                .filter(existing -> existing.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElseGet(() -> {
                    CartItem created = new CartItem();
                    created.setCart(cart);
                    created.setProduct(product);
                    created.setQuantity(0);
                    cart.getItems().add(created);
                    return created;
                });
        int nextQuantity = item.getQuantity() + request.getQuantity();
        validateStock(product, nextQuantity);
        item.setQuantity(nextQuantity);
        CartResponse response = mapCart(cartRepository.save(cart));
        redisStateStore.putCart(customerId, response);
        return response;
    }

    @Override
    @Transactional
    public CartResponse updateItem(UUID customerId, CartItemRequest request) {
        Cart cart = getOrCreateCart(customerId);
        Product product = loadProduct(request.getProductId());
        validateStock(product, request.getQuantity());
        CartItem item = findItem(cart, product.getId());
        item.setQuantity(request.getQuantity());
        CartResponse response = mapCart(cartRepository.save(cart));
        redisStateStore.putCart(customerId, response);
        return response;
    }

    @Override
    @Transactional
    public CartResponse removeItem(UUID customerId, UUID productId) {
        Cart cart = getOrCreateCart(customerId);
        cart.getItems().removeIf(item -> item.getProduct().getId().equals(productId));
        CartResponse response = mapCart(cartRepository.save(cart));
        redisStateStore.putCart(customerId, response);
        return response;
    }

    @Override
    @Transactional
    public CartResponse mergeGuestCart(UUID customerId, CartMergeRequest request) {
        if (request.getItems() != null) {
            request.getItems().forEach(item -> addItem(customerId, item));
        }
        CartResponse response = cartRepository.findByCustomerId(customerId).map(this::mapCart).orElseGet(this::emptyCart);
        redisStateStore.putCart(customerId, response);
        return response;
    }

    @Override
    @Transactional
    public void clearCart(UUID customerId) {
        cartRepository.findByCustomerId(customerId).ifPresent(cart -> {
            cart.getItems().clear();
            cartRepository.save(cart);
        });
        redisStateStore.deleteCart(customerId);
    }

    private Cart getOrCreateCart(UUID customerId) {
        return cartRepository.findByCustomerId(customerId).orElseGet(() -> {
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
            Cart cart = new Cart();
            cart.setCustomer(customer);
            cart.setItems(new ArrayList<>());
            return cartRepository.save(cart);
        });
    }

    private Product loadProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private CartItem findItem(Cart cart, UUID productId) {
        return cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
    }

    private void validateStock(Product product, int quantity) {
        if (quantity < 1) {
            throw new BusinessException("Quantity must be at least 1");
        }
        if (product.getQuantity() == null || product.getQuantity() < quantity) {
            throw new BusinessException("Requested quantity is not available for " + product.getName());
        }
    }

    private CartResponse mapCart(Cart cart) {
        var items = cart.getItems().stream().map(item -> {
            Product product = item.getProduct();
            BigDecimal lineTotal = product.getSellingPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            return CartItemResponse.builder()
                    .productId(product.getId())
                    .name(product.getName())
                    .sku(product.getSku())
                    .category(product.getCategory())
                    .imageDataUrl(product.getImageDataUrl())
                    .price(product.getSellingPrice())
                    .quantity(item.getQuantity())
                    .stockAvailable(product.getQuantity())
                    .lineTotal(lineTotal)
                    .build();
        }).toList();
        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return CartResponse.builder().items(items).subtotal(subtotal).build();
    }

    private CartResponse emptyCart() {
        return CartResponse.builder().items(List.of()).subtotal(BigDecimal.ZERO).build();
    }
}
