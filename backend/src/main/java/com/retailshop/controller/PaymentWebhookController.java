package com.retailshop.controller;

import com.retailshop.config.AppProperties;
import com.retailshop.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/razorpay")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final AppProperties appProperties;
    private final PaymentTransactionService paymentTransactionService;

    @PostMapping("/webhook")
    public Map<String, String> webhook(@RequestBody String payload,
                                       @RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        String signatureStatus = verifySignatureStatus(payload, signature);
        paymentTransactionService.recordWebhook(payload, signature, signatureStatus);
        return Map.of(
                "status", "received",
                "signatureStatus", signatureStatus
        );
    }

    private String verifySignatureStatus(String payload, String signature) {
        String webhookSecret = appProperties.getRazorpay().getWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return "NOT_CONFIGURED";
        }
        if (signature == null || signature.isBlank()) {
            return "MISSING";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder expected = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                expected.append(String.format("%02x", item));
            }
            return MessageDigest.isEqual(
                    expected.toString().getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            ) ? "VALID" : "INVALID";
        } catch (Exception exception) {
            return "ERROR";
        }
    }
}
