package com.retailshop.service.impl;

import com.retailshop.dto.CustomerProfileRequest;
import com.retailshop.dto.CustomerProfileResponse;
import com.retailshop.entity.Customer;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.service.CustomerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        customer.setName(request.getName().trim());
        return map(customerRepository.save(customer));
    }

    private CustomerProfileResponse map(Customer customer) {
        return CustomerProfileResponse.builder()
                .customerId(customer.getId())
                .name(customer.getName())
                .mobile(customer.getMobile())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}
