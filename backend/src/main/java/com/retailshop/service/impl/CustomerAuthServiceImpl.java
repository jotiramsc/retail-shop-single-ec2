package com.retailshop.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.dto.CustomerAuthResponse;
import com.retailshop.dto.CustomerGoogleLoginRequest;
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
import com.retailshop.service.OtpDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerAuthServiceImpl implements CustomerAuthService {

    private final AppProperties appProperties;
    private final CustomerJwtService customerJwtService;
    private final CustomerRepository customerRepository;
    private final CustomerOtpRepository customerOtpRepository;
    private final OtpDeliveryService otpDeliveryService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    @Transactional
    public CustomerOtpSendResponse sendOtp(CustomerOtpRequest request) {
        String mobile = normalizeLocalMobile(request.getMobile());
        String purpose = normalizePurpose(request.getPurpose(), "LOGIN");
        Optional<Customer> existingCustomer = findExistingCustomer(mobile);
        boolean customerExists = existingCustomer.isPresent();
        String channel = otpDeliveryService.getChannel();

        if ("LOGIN".equals(purpose) && !customerExists) {
            return CustomerOtpSendResponse.builder()
                    .otpRequired(false)
                    .customerExists(false)
                    .channel(channel)
                    .maskedMobile(maskMobile(mobile))
                    .nextStep("SIGNUP_REQUIRED")
                    .message("No customer account was found for this mobile number. Please sign up to continue.")
                    .build();
        }

        if ("SIGNUP".equals(purpose) && customerExists) {
            return CustomerOtpSendResponse.builder()
                    .otpRequired(false)
                    .customerExists(true)
                    .channel(channel)
                    .maskedMobile(maskMobile(mobile))
                    .nextStep("LOGIN_REQUIRED")
                    .message("An account already exists for this mobile number. Please login with OTP.")
                    .build();
        }

        if ("GOOGLE".equals(purpose) && hasText(request.getCustomerId())) {
            UUID pendingCustomerId = parseCustomerId(request.getCustomerId());
            customerRepository.findById(pendingCustomerId)
                    .orElseThrow(() -> new BusinessException("Google sign-in session expired. Please try again."));
            existingCustomer
                    .filter(existing -> !existing.getId().equals(pendingCustomerId))
                    .ifPresent(existing -> {
                        throw new BusinessException("This mobile number is already linked to another account");
                    });
        }

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

        if (otpDeliveryService.isConfigured()) {
            MarketingChannelResult result = otpDeliveryService.sendOtp(
                    mobile,
                    otp,
                    appProperties.getCustomerAuth().getOtpTtlMinutes()
            );
            if (result.isSuccess()) {
                return CustomerOtpSendResponse.builder()
                        .externalProviderConfigured(true)
                        .otpRequired(true)
                        .customerExists(customerExists)
                        .message("OTP sent on WhatsApp.")
                        .channel(channel)
                        .maskedMobile(maskMobile(mobile))
                        .nextStep("VERIFY_OTP")
                        .resendCooldownSeconds(resendCooldownSeconds)
                        .expiresInSeconds(expiresInSeconds)
                        .build();
            }
            String deliveryError = firstNonBlank(result.getErrorMessage(), "WhatsApp OTP could not be sent right now.");
            throw new BusinessException(deliveryError + " Please try again in a few minutes.");
        }

        throw new BusinessException("WhatsApp OTP provider is not configured. Please contact store support.");
    }

    @Override
    @Transactional
    public CustomerAuthResponse verifyOtp(CustomerOtpVerifyRequest request) {
        String mobile = normalizeLocalMobile(request.getMobile());
        String purpose = normalizePurpose(request.getPurpose(), "LOGIN");
        Optional<Customer> existingCustomer = findExistingCustomer(mobile);

        if ("LOGIN".equals(purpose) && existingCustomer.isEmpty()) {
            throw new BusinessException("No customer account was found for this mobile number. Please sign up to continue.");
        }
        if ("SIGNUP".equals(purpose) && existingCustomer.isPresent()) {
            throw new BusinessException("An account already exists for this mobile number. Please login with OTP.");
        }

        validateOtpAndDelete(mobile, request.getOtp());

        Customer customer = existingCustomer.orElseGet(() -> createOtpCustomer(mobile));
        customer.setMobile(formatDisplayMobile(mobile));
        customer.setMobileVerified(true);
        if (!hasText(customer.getAuthProvider())) {
            customer.setAuthProvider("OTP");
        }
        applyProfileCompletion(customer);
        customer = customerRepository.save(customer);

        return mapAuthResponse(customer);
    }

    @Override
    @Transactional
    public CustomerAuthResponse verifyGoogleMobileOtp(CustomerOtpVerifyRequest request) {
        UUID customerId = parseCustomerId(request.getCustomerId());
        String mobile = normalizeLocalMobile(request.getMobile());
        validateOtpAndDelete(mobile, request.getOtp());

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException("Google sign-in session expired. Please try again."));
        findExistingCustomer(mobile)
                .filter(existing -> !existing.getId().equals(customerId))
                .ifPresent(existing -> {
                    throw new BusinessException("This mobile number is already linked to another account");
                });

        customer.setMobile(formatDisplayMobile(mobile));
        customer.setMobileVerified(true);
        customer.setAuthProvider("GOOGLE");
        applyProfileCompletion(customer);
        return mapAuthResponse(customerRepository.save(customer));
    }

    @Override
    @Transactional
    public CustomerAuthResponse verifyOtpForCustomer(UUID customerId, CustomerOtpVerifyRequest request) {
        String mobile = normalizeLocalMobile(request.getMobile());
        validateOtpAndDelete(mobile, request.getOtp());

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException("Customer not found"));
        findExistingCustomer(mobile)
                .filter(existing -> !existing.getId().equals(customerId))
                .ifPresent(existing -> {
                    throw new BusinessException("This mobile number is already linked to another account");
                });

        customer.setMobile(formatDisplayMobile(mobile));
        customer.setMobileVerified(true);
        applyProfileCompletion(customer);
        return mapAuthResponse(customerRepository.save(customer));
    }

    @Override
    @Transactional
    public CustomerAuthResponse loginWithGoogle(CustomerGoogleLoginRequest request) {
        Map<String, Object> tokenPayload = verifyGoogleCredential(request.getCredential());
        String googleSubject = normalizeText((String) tokenPayload.get("sub"));
        String email = normalizeEmail((String) tokenPayload.get("email"));
        String name = normalizeText((String) tokenPayload.get("name"));

        if (!hasText(googleSubject)) {
            throw new BusinessException("Google sign-in did not return a valid account id");
        }
        if (!hasText(email)) {
            throw new BusinessException("Google sign-in did not return an email address");
        }

        Optional<Customer> bySubject = customerRepository.findByGoogleSubject(googleSubject);
        Optional<Customer> byEmail = customerRepository.findByEmailIgnoreCase(email);
        if (bySubject.isPresent() && byEmail.isPresent()
                && !bySubject.get().getId().equals(byEmail.get().getId())) {
            throw new BusinessException("This Google account email is already linked to another customer");
        }

        Customer customer = bySubject.or(() -> byEmail).orElseGet(Customer::new);
        customer.setGoogleSubject(googleSubject);
        customer.setEmail(email);
        customer.setEmailVerified(true);
        customer.setAuthProvider("GOOGLE");
        if (hasText(name) && !hasText(customer.getName())) {
            customer.setName(name);
        }
        applyProfileCompletion(customer);

        Customer savedCustomer = customerRepository.save(customer);
        boolean mobileReady = hasText(savedCustomer.getMobile()) && savedCustomer.isMobileVerified();
        return mapAuthResponse(savedCustomer, !mobileReady, mobileReady);
    }

    private Optional<Customer> findExistingCustomer(String mobile) {
        return safeOptional(customerRepository.findByMobile(mobile))
                .or(() -> safeOptional(customerRepository.findByMobile(formatDisplayMobile(mobile))))
                .or(() -> safeOptional(customerRepository.findByMobile("91" + mobile)))
                .or(() -> safeOptional(customerRepository.findByNormalizedMobile(mobile)));
    }

    private Customer createOtpCustomer(String mobile) {
        Customer created = new Customer();
        created.setMobile(formatDisplayMobile(mobile));
        try {
            return customerRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("An account already exists for this mobile number. Please login with OTP.");
        }
    }

    private void validateOtpAndDelete(String mobile, String otp) {
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

        if (!hashOtp(mobile, otp).equals(otpRecord.getOtpHash())) {
            otpRecord.setRetryCount(otpRecord.getRetryCount() + 1);
            customerOtpRepository.save(otpRecord);
            if (otpRecord.getRetryCount() >= maxAttempts) {
                customerOtpRepository.delete(otpRecord);
                throw new BusinessException("Too many invalid attempts. Please request a new OTP.");
            }
            throw new BusinessException("Invalid OTP");
        }

        customerOtpRepository.delete(otpRecord);
    }

    private Map<String, Object> verifyGoogleCredential(String credential) {
        String clientId = appProperties.getGoogleAuth().getClientId();
        if (!hasText(clientId)) {
            throw new BusinessException("Google Sign-In is not configured");
        }
        try {
            String encodedCredential = URLEncoder.encode(credential, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + encodedCredential))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("Google sign-in verification failed");
            }
            Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            if (!clientId.equals(String.valueOf(payload.get("aud")))) {
                throw new BusinessException("Google sign-in client id does not match this website");
            }
            if (!Boolean.parseBoolean(String.valueOf(payload.getOrDefault("email_verified", "false")))) {
                throw new BusinessException("Google email is not verified");
            }
            return payload;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Google sign-in verification was interrupted");
        } catch (IOException exception) {
            throw new BusinessException("Google sign-in verification failed");
        }
    }

    private CustomerAuthResponse mapAuthResponse(Customer customer) {
        return mapAuthResponse(customer, false, true);
    }

    private CustomerAuthResponse mapAuthResponse(Customer customer, boolean requiresMobileOtp, boolean issueToken) {
        List<String> missingFields = missingFields(customer);
        return CustomerAuthResponse.builder()
                .customerId(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .mobile(customer.getMobile())
                .authProvider(customer.getAuthProvider())
                .mobileVerified(customer.isMobileVerified())
                .emailVerified(customer.isEmailVerified())
                .profileComplete(missingFields.isEmpty())
                .requiresMobileOtp(requiresMobileOtp)
                .missingFields(missingFields)
                .token(issueToken ? "Bearer " + customerJwtService.issueToken(customer) : null)
                .build();
    }

    private void applyProfileCompletion(Customer customer) {
        List<String> missingFields = missingFields(customer);
        if (missingFields.isEmpty() && customer.getProfileCompletedAt() == null) {
            customer.setProfileCompletedAt(LocalDateTime.now());
        }
        if (!missingFields.isEmpty()) {
            customer.setProfileCompletedAt(null);
        }
    }

    private List<String> missingFields(Customer customer) {
        List<String> missing = new ArrayList<>();
        if (!hasText(customer.getMobile())) {
            missing.add("mobile number");
        }
        if (!customer.isMobileVerified()) {
            missing.add("mobile OTP verification");
        }
        return missing;
    }

    private String normalizePurpose(String purpose, String fallback) {
        String normalized = normalizeText(purpose);
        if (!hasText(normalized)) {
            return fallback;
        }
        return normalized.toUpperCase();
    }

    private Optional<Customer> safeOptional(Optional<Customer> optional) {
        return optional == null ? Optional.empty() : optional;
    }

    private UUID parseCustomerId(String value) {
        try {
            return UUID.fromString(normalizeText(value));
        } catch (RuntimeException exception) {
            throw new BusinessException("Google sign-in session expired. Please try again.");
        }
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

    private String normalizeEmail(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstNonBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
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
