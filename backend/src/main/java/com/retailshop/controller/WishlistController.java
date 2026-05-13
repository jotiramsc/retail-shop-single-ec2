package com.retailshop.controller;

import com.retailshop.dto.CartItemRequest;
import com.retailshop.dto.CartResponse;
import com.retailshop.dto.WishlistItemResponse;
import com.retailshop.entity.Customer;
import com.retailshop.entity.Product;
import com.retailshop.entity.WishlistItem;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.repository.WishlistItemRepository;
import com.retailshop.security.CustomerSecurity;
import com.retailshop.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistItemRepository wishlistItemRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;

    @GetMapping
    public List<WishlistItemResponse> getWishlist() {
        return wishlistItemRepository.findByCustomerIdOrderByCreatedAtDesc(CustomerSecurity.currentCustomerId())
                .stream()
                .map(this::map)
                .toList();
    }

    @PostMapping
    @Transactional
    public List<WishlistItemResponse> add(@Valid @RequestBody CartItemRequest request) {
        UUID customerId = CustomerSecurity.currentCustomerId();
        wishlistItemRepository.findByCustomerIdAndProductId(customerId, request.getProductId())
                .orElseGet(() -> wishlistItemRepository.save(createWishlistItem(customerId, request.getProductId())));
        return getWishlist();
    }

    @DeleteMapping
    @Transactional
    public List<WishlistItemResponse> remove(@RequestParam UUID productId) {
        wishlistItemRepository.deleteByCustomerIdAndProductId(CustomerSecurity.currentCustomerId(), productId);
        return getWishlist();
    }

    @PostMapping("/move-to-cart")
    @Transactional
    public CartResponse moveToCart(@Valid @RequestBody CartItemRequest request) {
        UUID customerId = CustomerSecurity.currentCustomerId();
        CartResponse cart = cartService.addItem(customerId, request);
        wishlistItemRepository.deleteByCustomerIdAndProductId(customerId, request.getProductId());
        return cart;
    }

    private WishlistItem createWishlistItem(UUID customerId, UUID productId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        WishlistItem item = new WishlistItem();
        item.setCustomer(customer);
        item.setProduct(product);
        return item;
    }

    private WishlistItemResponse map(WishlistItem item) {
        Product product = item.getProduct();
        return WishlistItemResponse.builder()
                .productId(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .category(product.getCategory())
                .imageDataUrl(product.getImageDataUrl())
                .price(product.getResolvedWebsitePrice())
                .stockAvailable(product.getQuantity())
                .inStock(product.getQuantity() != null && product.getQuantity() > 0)
                .createdAt(item.getCreatedAt())
                .build();
    }
}
