package com.retailshop.service.impl;

import com.retailshop.config.AppProperties;
import com.retailshop.dto.CustomerOtpRequest;
import com.retailshop.entity.CustomerOtp;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.CustomerOtpRepository;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.security.CustomerJwtService;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.WhatsAppMessageService;
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
    private WhatsAppMessageService whatsAppMessageService;

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
                whatsAppMessageService
        );
    }

    @Test
    void shouldReturnCooldownAndExpiryMetadataForWhatsAppOtp() {
        CustomerOtpRequest request = new CustomerOtpRequest();
        request.setMobile("+91 98765 43210");

        when(customerOtpRepository.findById("9876543210")).thenReturn(Optional.empty());
        when(customerOtpRepository.save(any(CustomerOtp.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(whatsAppMessageService.isConfigured()).thenReturn(true);
        when(whatsAppMessageService.sendOtp(eq("9876543210"), any(), eq(5L))).thenReturn(
                MarketingChannelResult.builder().success(true).build()
        );

        var response = customerAuthService.sendOtp(request);

        assertTrue(response.isExternalProviderConfigured());
        assertEquals("WHATSAPP", response.getChannel());
        assertEquals("+91 ••••••3210", response.getMaskedMobile());
        assertEquals(30L, response.getResendCooldownSeconds());
        assertEquals(300L, response.getExpiresInSeconds());
        assertFalse(response.getDevOtp().isBlank());

        ArgumentCaptor<CustomerOtp> otpCaptor = ArgumentCaptor.forClass(CustomerOtp.class);
        verify(customerOtpRepository).save(otpCaptor.capture());
        CustomerOtp savedOtp = otpCaptor.getValue();
        assertEquals("9876543210", savedOtp.getMobile());
        assertEquals("WHATSAPP", savedOtp.getChannel());
    }

    @Test
    void shouldRejectOtpRequestDuringCooldownWindow() {
        CustomerOtpRequest request = new CustomerOtpRequest();
        request.setMobile("9876543210");

        CustomerOtp existing = new CustomerOtp();
        existing.setMobile("9876543210");
        existing.setResendAllowedAt(LocalDateTime.now().plusSeconds(12));
        when(customerOtpRepository.findById("9876543210")).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class, () -> customerAuthService.sendOtp(request));

        assertTrue(exception.getMessage().contains("Please wait"));
    }
}
