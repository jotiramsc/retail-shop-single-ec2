package com.retailshop.controller;

import com.retailshop.dto.CartItemRequest;
import com.retailshop.dto.CartMergeRequest;
import com.retailshop.dto.CartResponse;
import com.retailshop.security.CustomerSecurity;
import com.retailshop.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse getCart() {
        return cartService.getCart(CustomerSecurity.currentCustomerId());
    }

    @PostMapping("/add")
    public CartResponse add(@Valid @RequestBody CartItemRequest request) {
        return cartService.addItem(CustomerSecurity.currentCustomerId(), request);
    }

    @PostMapping("/merge")
    public CartResponse merge(@RequestBody CartMergeRequest request) {
        return cartService.mergeGuestCart(CustomerSecurity.currentCustomerId(), request);
    }

    @PutMapping("/update")
    public CartResponse update(@Valid @RequestBody CartItemRequest request) {
        return cartService.updateItem(CustomerSecurity.currentCustomerId(), request);
    }

    @DeleteMapping("/remove")
    public CartResponse remove(@RequestParam UUID productId) {
        return cartService.removeItem(CustomerSecurity.currentCustomerId(), productId);
    }
}
