package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.dto.CustomerOtpRequest;
import com.retailshop.dto.CustomerOtpVerifyRequest;
import com.retailshop.entity.Customer;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
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
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customerOtpRepository.findById("9876543210")).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class, () -> customerAuthService.sendOtp(request));

        assertTrue(exception.getMessage().contains("Please wait"));
    }

    @Test
    void shouldSendSignupOtpForBillingCreatedUnverifiedCustomer() {
        Customer customer = new Customer();
        customer.setMobile("9876543210");
        customer.setCustomerSource("BILLING");
        customer.setVerificationStatus("UNVERIFIED");
        customer.setLoginEnabled(false);

        CustomerOtpRequest request = new CustomerOtpRequest();
        request.setMobile("9876543210");
        request.setPurpose("SIGNUP");

        when(customerRepository.findByMobile("9876543210")).thenReturn(Optional.of(customer));
        when(customerOtpRepository.findById("9876543210")).thenReturn(Optional.empty());
        when(customerOtpRepository.save(any(CustomerOtp.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(otpDeliveryService.isConfigured()).thenReturn(true);
        when(otpDeliveryService.getChannel()).thenReturn("WHATSAPP");
        when(otpDeliveryService.sendOtp(eq("9876543210"), any(), eq(5L))).thenReturn(
                MarketingChannelResult.builder().success(true).build()
        );

        var response = customerAuthService.sendOtp(request);

        assertTrue(response.isOtpRequired());
        assertTrue(response.isCustomerExists());
        assertEquals("VERIFY_OTP", response.getNextStep());
        assertEquals(
                "An account already exists with this mobile number from your previous purchase. Please verify using OTP to activate your account.",
                response.getMessage()
        );
    }

    @Test
    void shouldBlockSignupOtpForVerifiedCustomer() {
        Customer customer = new Customer();
        customer.setMobile("9876543210");
        customer.setVerificationStatus("VERIFIED");
        customer.setMobileVerified(true);
        customer.setLoginEnabled(true);

        CustomerOtpRequest request = new CustomerOtpRequest();
        request.setMobile("9876543210");
        request.setPurpose("SIGNUP");

        when(customerRepository.findByMobile("9876543210")).thenReturn(Optional.of(customer));
        when(otpDeliveryService.getChannel()).thenReturn("WHATSAPP");

        var response = customerAuthService.sendOtp(request);

        assertFalse(response.isOtpRequired());
        assertTrue(response.isCustomerExists());
        assertEquals("LOGIN_REQUIRED", response.getNextStep());
        assertEquals("An account already exists with this mobile number. Please login using OTP.", response.getMessage());
    }

    @Test
    void shouldActivateBillingCreatedCustomerAfterOtpVerification() {
        Customer customer = new Customer();
        customer.setMobile("9876543210");
        customer.setCustomerSource("BILLING");
        customer.setVerificationStatus("UNVERIFIED");
        customer.setLoginEnabled(false);

        CustomerOtp otp = otpRecord("9876543210", "123456", LocalDateTime.now().plusMinutes(5), 0);
        CustomerOtpVerifyRequest request = new CustomerOtpVerifyRequest();
        request.setMobile("9876543210");
        request.setOtp("123456");
        request.setPurpose("SIGNUP");

        when(customerRepository.findByMobile("9876543210")).thenReturn(Optional.of(customer));
        when(customerOtpRepository.findById("9876543210")).thenReturn(Optional.of(otp));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customerJwtService.issueToken(any(Customer.class))).thenReturn("token");

        var response = customerAuthService.verifyOtp(request);

        assertTrue(customer.isMobileVerified());
        assertTrue(customer.isLoginEnabled());
        assertEquals("VERIFIED", customer.getVerificationStatus());
        assertEquals("BILLING", customer.getCustomerSource());
        assertTrue(customer.getOtpVerifiedAt() != null);
        assertEquals("Bearer token", response.getToken());
        assertEquals("VERIFIED", response.getVerificationStatus());
        assertTrue(response.isLoginEnabled());
    }

    @Test
    void shouldRejectUnknownLoginWithoutCreatingCustomer() {
        CustomerOtpVerifyRequest request = new CustomerOtpVerifyRequest();
        request.setMobile("9876543210");
        request.setOtp("123456");
        request.setPurpose("LOGIN");

        BusinessException exception = assertThrows(BusinessException.class, () -> customerAuthService.verifyOtp(request));

        assertEquals("No account found with this mobile number. Please sign up first.", exception.getMessage());
    }

    @Test
    void shouldRejectExpiredOtpWithUserFacingMessage() {
        Customer customer = new Customer();
        customer.setMobile("9876543210");
        CustomerOtpVerifyRequest request = new CustomerOtpVerifyRequest();
        request.setMobile("9876543210");
        request.setOtp("123456");
        request.setPurpose("LOGIN");

        when(customerRepository.findByMobile("9876543210")).thenReturn(Optional.of(customer));
        when(customerOtpRepository.findById("9876543210"))
                .thenReturn(Optional.of(otpRecord("9876543210", "123456", LocalDateTime.now().minusMinutes(1), 0)));

        BusinessException exception = assertThrows(BusinessException.class, () -> customerAuthService.verifyOtp(request));

        assertEquals("OTP has expired. Please request a new OTP.", exception.getMessage());
    }

    @Test
    void shouldRejectInvalidOtpWithUserFacingMessage() {
        Customer customer = new Customer();
        customer.setMobile("9876543210");
        CustomerOtpVerifyRequest request = new CustomerOtpVerifyRequest();
        request.setMobile("9876543210");
        request.setOtp("000000");
        request.setPurpose("LOGIN");

        when(customerRepository.findByMobile("9876543210")).thenReturn(Optional.of(customer));
        when(customerOtpRepository.findById("9876543210"))
                .thenReturn(Optional.of(otpRecord("9876543210", "123456", LocalDateTime.now().plusMinutes(5), 0)));
        when(customerOtpRepository.save(any(CustomerOtp.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BusinessException exception = assertThrows(BusinessException.class, () -> customerAuthService.verifyOtp(request));

        assertEquals("Invalid OTP. Please try again.", exception.getMessage());
    }

    private CustomerOtp otpRecord(String mobile, String otp, LocalDateTime expiry, int retryCount) {
        CustomerOtp record = new CustomerOtp();
        record.setMobile(mobile);
        record.setOtpHash(hashOtp(mobile, otp));
        record.setExpiry(expiry);
        record.setRetryCount(retryCount);
        return record;
    }

    private String hashOtp(String mobile, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((mobile + ":" + otp + ":local-dev-customer-secret-change-me")
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
