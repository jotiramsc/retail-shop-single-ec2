package com.retailshop.controller;

import com.retailshop.dto.OrderResponse;
import com.retailshop.dto.PlaceOrderRequest;
import com.retailshop.security.CustomerSecurity;
import com.retailshop.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/order/place")
    public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(CustomerSecurity.currentCustomerId(), request);
    }

    @GetMapping("/orders")
    public List<OrderResponse> getOrders() {
        return orderService.getOrders(CustomerSecurity.currentCustomerId());
    }
}
