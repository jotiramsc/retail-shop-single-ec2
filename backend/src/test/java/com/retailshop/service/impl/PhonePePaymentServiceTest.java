package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.dto.PlaceOrderRequest;
import com.retailshop.exception.BusinessException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhonePePaymentServiceTest {

    @Test
    void shouldReturnLocalRazorpayOrderWhenRazorpaySecretIsNotConfigured() {
        PhonePePaymentService service = new PhonePePaymentService(new AppProperties(), new ObjectMapper());

        var response = service.createPaymentOrder(BigDecimal.valueOf(250.00), "chk-1", "http://localhost:5176/checkout");

        assertFalse(response.isConfigured());
        assertEquals("RAZORPAY", response.getProvider());
        assertEquals("rzp_test_SirLkQHBMbkN2d", response.getKeyId());
        assertEquals(BigDecimal.valueOf(250.00).setScale(2), response.getAmount());
    }

    @Test
    void shouldSurfaceAuthenticationFailureForInvalidConfiguredPhonePeCredentials() {
        AppProperties properties = new AppProperties();
        properties.getPayment().setProvider("PHONEPE");
        properties.getPhonepe().setClientId("client-id");
        properties.getPhonepe().setClientSecret("client-secret");
        properties.getPhonepe().setRedirectUrl("http://retail-shop-single-alb.example.com/checkout");

        PhonePePaymentService service = new PhonePePaymentService(properties, new ObjectMapper());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createPaymentOrder(BigDecimal.TEN, "chk-2", "http://retail-shop-single-alb.example.com/checkout")
        );

        assertEquals(
                "PhonePe authentication failed. Check the client id, client secret, environment, and client version configured on the server.",
                exception.getMessage()
        );
    }

    @Test
    void shouldVerifyRazorpaySignature() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getRazorpay().setKeyId("rzp_test_key");
        properties.getRazorpay().setKeySecret("secret");
        PhonePePaymentService service = new PhonePePaymentService(properties, new ObjectMapper());
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
