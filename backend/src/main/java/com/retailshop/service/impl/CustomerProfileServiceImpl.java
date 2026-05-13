package com.retailshop.service.impl;

import com.retailshop.dto.CustomerProfileRequest;
import com.retailshop.dto.CustomerProfileResponse;
import com.retailshop.entity.Customer;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.service.CustomerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerProfileServiceImpl implements CustomerProfileService {

    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfile(UUID customerId) {
        return map(customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found")));
    }

    @Override
    @Transactional
    public CustomerProfileResponse updateProfile(UUID customerId, CustomerProfileRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        String nextName = normalizeText(request.getName());
        String nextEmail = normalizeEmail(request.getEmail());
        String nextMobile = normalizeText(request.getMobile());

        customer.setName(nextName);
        if (hasText(nextEmail)) {
            customerRepository.findByEmailIgnoreCase(nextEmail)
                    .filter(existing -> !existing.getId().equals(customerId))
                    .ifPresent(existing -> {
                        throw new BusinessException("This email address is already linked to another account");
                    });
            customer.setEmail(nextEmail);
            customer.setEmailVerified(customer.isEmailVerified() || "GOOGLE".equalsIgnoreCase(customer.getAuthProvider()));
        } else if (!"GOOGLE".equalsIgnoreCase(customer.getAuthProvider())) {
            customer.setEmail(null);
            customer.setEmailVerified(false);
        }

        if (hasText(nextMobile)) {
            String normalizedNextMobile = normalizeLocalMobile(nextMobile);
            findExistingCustomer(normalizedNextMobile)
                    .filter(existing -> !existing.getId().equals(customerId))
                    .ifPresent(existing -> {
                        throw new BusinessException("This mobile number is already linked to another account");
                    });

            String normalizedCurrentMobile = normalizeLocalMobileOrBlank(customer.getMobile());
            boolean mobileChanged = !normalizedNextMobile.equals(normalizedCurrentMobile);
            customer.setMobile(formatDisplayMobile(normalizedNextMobile));
            if (mobileChanged) {
                customer.setMobileVerified(false);
            }
        }
        customer.setDateOfBirth(request.getDateOfBirth());
        customer.setGender(normalizeText(request.getGender()));
        customer.setProfileImageUrl(normalizeText(request.getProfileImageUrl()));
        customer.setAlternateMobile(normalizeOptionalMobile(request.getAlternateMobile()));
        applyProfileCompletion(customer);
        return map(customerRepository.save(customer));
    }

    @Override
    @Transactional(readOnly = true)
    public void ensureCheckoutReady(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        List<String> missingFields = missingFields(customer);
        if (!missingFields.isEmpty()) {
            throw new BusinessException("Mobile OTP verification is required before payment");
        }
    }

    private CustomerProfileResponse map(Customer customer) {
        List<String> missingFields = missingFields(customer);
        return CustomerProfileResponse.builder()
                .customerId(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .mobile(customer.getMobile())
                .dateOfBirth(customer.getDateOfBirth())
                .gender(customer.getGender())
                .profileImageUrl(customer.getProfileImageUrl())
                .alternateMobile(customer.getAlternateMobile())
                .authProvider(customer.getAuthProvider())
                .mobileVerified(customer.isMobileVerified())
                .emailVerified(customer.isEmailVerified())
                .profileComplete(missingFields.isEmpty())
                .missingFields(missingFields)
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .profileCompletedAt(customer.getProfileCompletedAt())
                .build();
    }

    private void applyProfileCompletion(Customer customer) {
        if (missingFields(customer).isEmpty() && customer.getProfileCompletedAt() == null) {
            customer.setProfileCompletedAt(LocalDateTime.now());
        }
        if (!missingFields(customer).isEmpty()) {
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

    private Optional<Customer> findExistingCustomer(String mobile) {
        return customerRepository.findByMobile(mobile)
                .or(() -> customerRepository.findByMobile(formatDisplayMobile(mobile)))
                .or(() -> customerRepository.findByMobile("91" + mobile))
                .or(() -> customerRepository.findByNormalizedMobile(mobile));
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

    private String normalizeLocalMobileOrBlank(String mobile) {
        String digits = mobile == null ? "" : mobile.replaceAll("[^0-9]", "");
        if (digits.startsWith("91") && digits.length() > 10) {
            digits = digits.substring(digits.length() - 10);
        }
        return digits.length() == 10 ? digits : "";
    }

    private String normalizeOptionalMobile(String mobile) {
        if (!hasText(mobile)) {
            return null;
        }
        return formatDisplayMobile(normalizeLocalMobile(mobile));
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
}
