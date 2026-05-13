package com.retailshop.controller;

import com.retailshop.dto.CheckoutQuoteResponse;
import com.retailshop.dto.CouponRequest;
import com.retailshop.dto.PaymentOrderRequest;
import com.retailshop.dto.PaymentOrderResponse;
import com.retailshop.dto.PaymentStatusResponse;
import com.retailshop.exception.BusinessException;
import com.retailshop.security.CustomerSecurity;
import com.retailshop.service.CheckoutService;
import com.retailshop.service.CustomerProfileService;
import com.retailshop.service.PaymentService;
import com.retailshop.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final PaymentService paymentService;
    private final PaymentTransactionService paymentTransactionService;
    private final CustomerProfileService customerProfileService;

    @GetMapping("/offers/applicable")
    public CheckoutQuoteResponse applicableOffers(@RequestParam(required = false) String couponCode) {
        return checkoutService.quote(CustomerSecurity.currentCustomerId(), couponCode);
    }

    @PostMapping("/checkout/apply-coupon")
    public CheckoutQuoteResponse applyCoupon(@RequestBody CouponRequest request) {
        return checkoutService.quote(CustomerSecurity.currentCustomerId(), request.getCouponCode());
    }

    @PostMapping("/checkout/payment-order")
    public PaymentOrderResponse createPaymentOrder(@RequestBody(required = false) PaymentOrderRequest request) {
        UUID customerId = CustomerSecurity.currentCustomerId();
        customerProfileService.ensureCheckoutReady(customerId);
        CheckoutQuoteResponse quote = checkoutService.quote(
                customerId,
                request != null ? request.getCouponCode() : null
        );
        if (quote.getCart() == null || quote.getCart().getItems() == null || quote.getCart().getItems().isEmpty()) {
            throw new BusinessException("Cart is empty");
        }
        PaymentOrderResponse response = paymentService.createPaymentOrder(
                quote.getFinalTotal(),
                "chk-" + System.currentTimeMillis(),
                request != null ? request.getRedirectUrl() : null
        );
        paymentTransactionService.attachCustomerContext(response.getOrderId(), customerId);
        return response;
    }

    @GetMapping("/checkout/payment-status")
    public PaymentStatusResponse paymentStatus(@RequestParam String merchantOrderId) {
        return paymentService.getPaymentStatus(merchantOrderId);
    }
}
