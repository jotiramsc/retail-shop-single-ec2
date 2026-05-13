package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.dto.PlaceOrderRequest;
import com.retailshop.service.PaymentTransactionService;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RazorpayPaymentServiceTest {

    @Test
    void shouldOpenTestRazorpayCheckoutWhenOnlyKeyIdIsConfigured() {
        RazorpayPaymentService service = new RazorpayPaymentService(new AppProperties(), new ObjectMapper(), mock(PaymentTransactionService.class));

        var response = service.createPaymentOrder(BigDecimal.valueOf(250.00), "chk-1", "http://localhost:5176/checkout");

        assertTrue(response.isConfigured());
        assertEquals("RAZORPAY", response.getProvider());
        assertEquals("rzp_test_SirLkQHBMbkN2d", response.getKeyId());
        assertTrue(response.getOrderId().startsWith("RZP-"));
        assertEquals(BigDecimal.valueOf(250.00).setScale(2), response.getAmount());
    }

    @Test
    void shouldVerifyRazorpaySignature() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getRazorpay().setKeyId("rzp_test_key");
        properties.getRazorpay().setKeySecret("secret");
        RazorpayPaymentService service = new RazorpayPaymentService(properties, new ObjectMapper(), mock(PaymentTransactionService.class));
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setPaymentProvider("RAZORPAY");
        request.setRazorpayOrderId("order_123");
        request.setRazorpayPaymentId("pay_456");
        request.setRazorpaySignature(hmac("order_123|pay_456", "secret"));

        assertTrue(service.verifyPayment(request, BigDecimal.TEN));
    }

    private String hmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }
}
