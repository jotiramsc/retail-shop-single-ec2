package com.retailshop.service.impl;

import com.retailshop.entity.Customer;
import com.retailshop.repository.AddressRepository;
import com.retailshop.repository.CustomerActivityHistoryRepository;
import com.retailshop.repository.CustomerLocationHistoryRepository;
import com.retailshop.repository.CustomerLoginHistoryRepository;
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.InvoiceRepository;
import com.retailshop.repository.OmnichannelConversationMessageRepository;
import com.retailshop.repository.OmnichannelConversationRepository;
import com.retailshop.repository.OmnichannelLeadRepository;
import com.retailshop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CustomerOrderRepository customerOrderRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private CustomerLoginHistoryRepository loginHistoryRepository;
    @Mock private CustomerLocationHistoryRepository locationHistoryRepository;
    @Mock private CustomerActivityHistoryRepository activityHistoryRepository;
    @Mock private InvoiceItemRepository invoiceItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OmnichannelLeadRepository omnichannelLeadRepository;
    @Mock private OmnichannelConversationRepository conversationRepository;
    @Mock private OmnichannelConversationMessageRepository messageRepository;

    private CustomerServiceImpl customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerServiceImpl(
                customerRepository,
                invoiceRepository,
                customerOrderRepository,
                addressRepository,
                loginHistoryRepository,
                locationHistoryRepository,
                activityHistoryRepository,
                invoiceItemRepository,
                productRepository,
                omnichannelLeadRepository,
                conversationRepository,
                messageRepository
        );
    }

    @Test
    void billingCreatesUnverifiedLoginDisabledCustomer() {
        when(customerRepository.findByMobile("9876543210")).thenReturn(Optional.empty());
        when(customerRepository.findByNormalizedMobile("9876543210")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer customer = customerService.findOrCreateCustomer("Billing Buyer", "+91 98765 43210");

        assertEquals("Billing Buyer", customer.getName());
        assertEquals("9876543210", customer.getMobile());
        assertEquals("BILLING", customer.getCustomerSource());
        assertEquals("UNVERIFIED", customer.getVerificationStatus());
        assertFalse(customer.isLoginEnabled());
        assertFalse(customer.isMobileVerified());
        assertNotNull(customer.getLastOrderAt());
    }

    @Test
    void billingReusesExistingVerifiedCustomerWithoutOverwritingName() {
        Customer existing = new Customer();
        existing.setName("Verified Name");
        existing.setMobile("9876543210");
        existing.setVerificationStatus("VERIFIED");
        existing.setMobileVerified(true);
        existing.setLoginEnabled(true);

        when(customerRepository.findByMobile("9876543210")).thenReturn(Optional.of(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer customer = customerService.findOrCreateCustomer("New Billing Name", "9876543210");

        assertEquals("Verified Name", customer.getName());
        assertEquals("VERIFIED", customer.getVerificationStatus());
        assertEquals("BOTH", customer.getCustomerSource());
        assertNotNull(customer.getLastOrderAt());
    }
}
