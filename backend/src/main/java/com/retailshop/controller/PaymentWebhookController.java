package com.retailshop.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/phonepe", "/api/razorpay"})
public class PaymentWebhookController {

    @PostMapping("/webhook")
    public Map<String, String> webhook(@RequestBody String payload,
                                       @RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestHeader(value = "X-VERIFY", required = false) String verificationHeader,
                                       @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        return Map.of("status", "received");
    }
}
