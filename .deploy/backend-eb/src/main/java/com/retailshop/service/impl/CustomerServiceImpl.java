package com.retailshop.service.impl;

import com.retailshop.dto.CustomerRequest;
import com.retailshop.dto.CustomerResponse;
import com.retailshop.dto.CustomerLookupResponse;
import com.retailshop.dto.PurchaseHistoryResponse;
import com.retailshop.entity.Customer;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.repository.InvoiceRepository;
import com.retailshop.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        customerRepository.findByMobile(request.getMobile()).ifPresent(customer -> {
            throw new BusinessException("Customer with this mobile already exists");
        });
        Customer customer = new Customer();
        customer.setName(request.getName());
        customer.setMobile(request.getMobile());
        return mapToResponse(customerRepository.save(customer));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> searchCustomers(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 1) {
            return List.of();
        }

        return customerRepository.searchByNameOrMobile(normalizedQuery)
                .stream()
                .limit(8)
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseHistoryResponse> getPurchaseHistory(String mobile) {
        Customer customer = customerRepository.findByMobile(mobile)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return invoiceRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .map(invoice -> PurchaseHistoryResponse.builder()
                        .invoiceId(invoice.getId())
                        .invoiceNumber(invoice.getInvoiceNumber())
                        .finalAmount(invoice.getFinalAmount())
                        .createdAt(invoice.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerLookupResponse lookupCustomer(String mobile) {
        Customer customer = customerRepository.findByMobile(mobile)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        var invoices = invoiceRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
        return CustomerLookupResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .mobile(customer.getMobile())
                .totalInvoices(invoices.size())
                .totalSpent(invoices.stream()
                        .map(invoice -> Optional.ofNullable(invoice.getFinalAmount()).orElse(java.math.BigDecimal.ZERO))
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .lastPurchaseAt(invoices.stream()
                        .findFirst()
                        .map(invoice -> invoice.getCreatedAt())
                        .orElse(null))
                .build();
    }

    @Override
    @Transactional
    public Customer findOrCreateCustomer(String name, String mobile) {
        return customerRepository.findByMobile(mobile)
                .map(existing -> {
                    if (existing.getName() == null || !existing.getName().equals(name)) {
                        existing.setName(name);
                    }
                    return customerRepository.save(existing);
                })
                .orElseGet(() -> {
                    Customer customer = new Customer();
                    customer.setName(name);
                    customer.setMobile(mobile);
                    return customerRepository.save(customer);
                });
    }

    private CustomerResponse mapToResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .mobile(customer.getMobile())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}
