package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhonePePaymentServiceTest {

    @Test
    void shouldReturnLocalPaymentOrderWhenPhonePeIsNotConfigured() {
        PhonePePaymentService service = new PhonePePaymentService(new AppProperties(), new ObjectMapper());

        var response = service.createPaymentOrder(BigDecimal.valueOf(250.00), "chk-1", "http://localhost:5176/checkout");

        assertFalse(response.isConfigured());
        assertEquals("PHONEPE", response.getProvider());
        assertEquals(BigDecimal.valueOf(250.00).setScale(2), response.getAmount());
    }

    @Test
    void shouldSurfaceAuthenticationFailureForInvalidConfiguredPhonePeCredentials() {
        AppProperties properties = new AppProperties();
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
}
