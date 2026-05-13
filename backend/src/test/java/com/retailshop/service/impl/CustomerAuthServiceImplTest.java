package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.dto.CustomerOtpRequest;
import com.retailshop.entity.CustomerOtp;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.CustomerOtpRepository;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.security.CustomerJwtService;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.OtpDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAuthServiceImplTest {

    @Mock
    private CustomerJwtService customerJwtService;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerOtpRepository customerOtpRepository;

    @Mock
    private OtpDeliveryService otpDeliveryService;

    private CustomerAuthServiceImpl customerAuthService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getCustomerAuth().setOtpRateLimitSeconds(30);
        appProperties.getCustomerAuth().setOtpTtlMinutes(5);
        appProperties.getCustomerAuth().setOtpLength(6);
        customerAuthService = new CustomerAuthServiceImpl(
                appProperties,
                customerJwtService,
                customerRepository,
                customerOtpRepository,
                otpDeliveryService,
                new ObjectMapper()
        );
    }

    @Test
    void shouldReturnCooldownAndExpiryMetadataForWhatsAppOtp() {
        CustomerOtpRequest request = new CustomerOtpRequest();
        request.setMobile("+91 98765 43210");
        request.setPurpose("SIGNUP");

        when(customerOtpRepository.findById("9876543210")).thenReturn(Optional.empty());
        when(customerOtpRepository.save(any(CustomerOtp.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(otpDeliveryService.isConfigured()).thenReturn(true);
        when(otpDeliveryService.sendOtp(eq("9876543210"), any(), eq(5L))).thenReturn(
                MarketingChannelResult.builder().success(true).build()
        );
        when(otpDeliveryService.getChannel()).thenReturn("WHATSAPP");

        var response = customerAuthService.sendOtp(request);

        assertTrue(response.isExternalProviderConfigured());
        assertTrue(response.isOtpRequired());
        assertFalse(response.isCustomerExists());
        assertEquals("WHATSAPP", response.getChannel());
        assertEquals("+91 ••••••3210", response.getMaskedMobile());
        assertEquals(30L, response.getResendCooldownSeconds());
        assertEquals(300L, response.getExpiresInSeconds());
        ArgumentCaptor<CustomerOtp> otpCaptor = ArgumentCaptor.forClass(CustomerOtp.class);
        verify(customerOtpRepository).save(otpCaptor.capture());
        CustomerOtp savedOtp = otpCaptor.getValue();
        assertEquals("9876543210", savedOtp.getMobile());
        assertEquals("WHATSAPP", savedOtp.getChannel());
    }

    @Test
    void shouldRejectOtpRequestWhenConfiguredProviderFails() {
        CustomerOtpRequest request = new CustomerOtpRequest();
        request.setMobile("+91 98765 43210");
        request.setPurpose("SIGNUP");

        when(customerOtpRepository.findById("9876543210")).thenReturn(Optional.empty());
        when(customerOtpRepository.save(any(CustomerOtp.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(otpDeliveryService.isConfigured()).thenReturn(true);
        when(otpDeliveryService.sendOtp(eq("9876543210"), any(), eq(5L))).thenReturn(
                MarketingChannelResult.builder().success(false).errorMessage("Meta send failed").build()
        );
        when(otpDeliveryService.getChannel()).thenReturn("WHATSAPP");

        BusinessException exception = assertThrows(BusinessException.class, () -> customerAuthService.sendOtp(request));

        assertTrue(exception.getMessage().contains("Meta send failed"));
        assertTrue(exception.getMessage().contains("Please try again"));
    }

    @Test
    void shouldRejectOtpRequestDuringCooldownWindow() {
        CustomerOtpRequest request = new CustomerOtpRequest();
        request.setMobile("9876543210");
        request.setPurpose("SIGNUP");

        CustomerOtp existing = new CustomerOtp();
        existing.setMobile("9876543210");
        existing.setResendAllowedAt(LocalDateTime.now().plusSeconds(12));
        when(customerOtpRepository.findById("9876543210")).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class, () -> customerAuthService.sendOtp(request));

        assertTrue(exception.getMessage().contains("Please wait"));
    }
}
