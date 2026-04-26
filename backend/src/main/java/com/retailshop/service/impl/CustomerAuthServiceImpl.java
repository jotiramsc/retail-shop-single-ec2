package com.retailshop.service.impl;

import com.retailshop.config.AppProperties;
import com.retailshop.dto.CustomerAuthResponse;
import com.retailshop.dto.CustomerOtpRequest;
import com.retailshop.dto.CustomerOtpSendResponse;
import com.retailshop.dto.CustomerOtpVerifyRequest;
import com.retailshop.entity.Customer;
import com.retailshop.entity.CustomerOtp;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.CustomerOtpRepository;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.security.CustomerJwtService;
import com.retailshop.service.CustomerAuthService;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.WhatsAppMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerAuthServiceImpl implements CustomerAuthService {

    private final AppProperties appProperties;
    private final CustomerJwtService customerJwtService;
    private final CustomerRepository customerRepository;
    private final CustomerOtpRepository customerOtpRepository;
    private final WhatsAppMessageService whatsAppMessageService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public CustomerOtpSendResponse sendOtp(CustomerOtpRequest request) {
        String mobile = normalizeLocalMobile(request.getMobile());
        LocalDateTime now = LocalDateTime.now();
        CustomerOtp otpRecord = customerOtpRepository.findById(mobile).orElse(null);
        long resendCooldownSeconds = Math.max(1, appProperties.getCustomerAuth().getOtpRateLimitSeconds());
        long expiresInSeconds = Math.max(60, appProperties.getCustomerAuth().getOtpTtlMinutes() * 60);

        if (otpRecord != null && otpRecord.getResendAllowedAt() != null && now.isBefore(otpRecord.getResendAllowedAt())) {
            long waitSeconds = Math.max(1, Duration.between(now, otpRecord.getResendAllowedAt()).getSeconds());
            throw new BusinessException("Please wait " + waitSeconds + " seconds before requesting another OTP");
        }

        String otp = generateOtp();
        CustomerOtp nextRecord = otpRecord == null ? new CustomerOtp() : otpRecord;
        nextRecord.setMobile(mobile);
        nextRecord.setOtpHash(hashOtp(mobile, otp));
        nextRecord.setChannel("WHATSAPP");
        nextRecord.setExpiry(now.plusMinutes(appProperties.getCustomerAuth().getOtpTtlMinutes()));
        nextRecord.setRetryCount(0);
        nextRecord.setResendAllowedAt(now.plusSeconds(resendCooldownSeconds));
        customerOtpRepository.save(nextRecord);

        if (whatsAppMessageService.isConfigured()) {
            MarketingChannelResult result = whatsAppMessageService.sendOtp(
                    mobile,
                    otp,
                    appProperties.getCustomerAuth().getOtpTtlMinutes()
            );
            if (!result.isSuccess()) {
                throw new BusinessException(result.getErrorMessage() == null
                        ? "Unable to send OTP on WhatsApp"
                        : result.getErrorMessage());
            }
            return CustomerOtpSendResponse.builder()
                    .externalProviderConfigured(true)
                    .message("OTP sent on WhatsApp.")
                    .channel("WHATSAPP")
                    .maskedMobile(maskMobile(mobile))
                    .resendCooldownSeconds(resendCooldownSeconds)
                    .expiresInSeconds(expiresInSeconds)
                    .build();
        }

        return CustomerOtpSendResponse.builder()
                .externalProviderConfigured(false)
                .message("WhatsApp OTP is not configured, so a local testing code was generated.")
                .devOtp(otp)
                .channel("WHATSAPP")
                .maskedMobile(maskMobile(mobile))
                .resendCooldownSeconds(resendCooldownSeconds)
                .expiresInSeconds(expiresInSeconds)
                .build();
    }

    @Override
    @Transactional
    public CustomerAuthResponse verifyOtp(CustomerOtpVerifyRequest request) {
        String mobile = normalizeLocalMobile(request.getMobile());
        CustomerOtp otpRecord = customerOtpRepository.findById(mobile)
                .orElseThrow(() -> new BusinessException("OTP expired. Please request a new OTP."));

        LocalDateTime now = LocalDateTime.now();
        if (otpRecord.getExpiry() == null || now.isAfter(otpRecord.getExpiry())) {
            customerOtpRepository.delete(otpRecord);
            throw new BusinessException("OTP expired. Please request a new OTP.");
        }

        int maxAttempts = Math.max(1, appProperties.getCustomerAuth().getOtpMaxAttempts());
        if (otpRecord.getRetryCount() >= maxAttempts) {
            customerOtpRepository.delete(otpRecord);
            throw new BusinessException("Too many invalid attempts. Please request a new OTP.");
        }

        if (!hashOtp(mobile, request.getOtp()).equals(otpRecord.getOtpHash())) {
            otpRecord.setRetryCount(otpRecord.getRetryCount() + 1);
            customerOtpRepository.save(otpRecord);
            if (otpRecord.getRetryCount() >= maxAttempts) {
                customerOtpRepository.delete(otpRecord);
                throw new BusinessException("Too many invalid attempts. Please request a new OTP.");
            }
            throw new BusinessException("Invalid OTP");
        }

        customerOtpRepository.delete(otpRecord);

        Customer customer = findExistingCustomer(mobile)
                .orElseGet(() -> {
                    Customer created = new Customer();
                    created.setMobile(formatDisplayMobile(mobile));
                    created.setName("Customer " + mobile.substring(Math.max(0, mobile.length() - 4)));
                    return customerRepository.save(created);
                });

        return CustomerAuthResponse.builder()
                .customerId(customer.getId())
                .name(customer.getName())
                .mobile(customer.getMobile())
                .token("Bearer " + customerJwtService.issueToken(customer))
                .build();
    }

    private Optional<Customer> findExistingCustomer(String mobile) {
        return customerRepository.findByMobile(mobile)
                .or(() -> customerRepository.findByMobile(formatDisplayMobile(mobile)))
                .or(() -> customerRepository.findByMobile("91" + mobile));
    }

    private String normalizeLocalMobile(String mobile) {
        String digits = mobile == null ? "" : mobile.replaceAll("[^0-9]", "");
        if (digits.startsWith("91") && digits.length() > 10) {
            digits = digits.substring(digits.length() - 10);
        }
        if (digits.length() != 10) {
            throw new BusinessException("Valid mobile number is required");
        }
        return digits;
    }

    private String formatDisplayMobile(String mobile) {
        return "+91 " + mobile;
    }

    private String maskMobile(String mobile) {
        String lastFour = mobile.substring(Math.max(0, mobile.length() - 4));
        return "+91 ••••••" + lastFour;
    }

    private String generateOtp() {
        int otpLength = Math.max(4, appProperties.getCustomerAuth().getOtpLength());
        int upperBound = (int) Math.pow(10, otpLength);
        int minimum = (int) Math.pow(10, otpLength - 1);
        return String.valueOf(minimum + secureRandom.nextInt(upperBound - minimum));
    }

    private String hashOtp(String mobile, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String pepper = appProperties.getCustomerAuth().getJwtSecret();
            byte[] hash = digest.digest((mobile + ":" + String.valueOf(otp).trim() + ":" + pepper)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash OTP", exception);
        }
    }
}
