package com.retailshop.service;

import com.retailshop.dto.CustomerRequest;
import com.retailshop.dto.CustomerActivityTrackRequest;
import com.retailshop.dto.CustomerDetailsResponse;
import com.retailshop.dto.CustomerEngagementUpdateRequest;
import com.retailshop.dto.CustomerLocationUpdateRequest;
import com.retailshop.dto.CustomerResponse;
import com.retailshop.dto.CustomerLookupResponse;
import com.retailshop.dto.CustomerSupportChatResponse;
import com.retailshop.dto.PurchaseHistoryResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.entity.Customer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CustomerService {
    CustomerResponse createCustomer(CustomerRequest request);
    PaginatedResponse<CustomerResponse> getAllCustomers(Pageable pageable, String segment);
    List<CustomerResponse> searchCustomers(String query);
    CustomerDetailsResponse getCustomerDetails(UUID customerId);
    CustomerDetailsResponse updateCustomerEngagement(UUID customerId, CustomerEngagementUpdateRequest request);
    List<PurchaseHistoryResponse> getPurchaseHistory(String mobile);
    CustomerLookupResponse lookupCustomer(String mobile);
    Customer findOrCreateCustomer(String name, String mobile);
    void recordLogin(UUID customerId, String method, String status, HttpServletRequest request);
    void recordActivity(UUID customerId, CustomerActivityTrackRequest request);
    void recordLocation(UUID customerId, CustomerLocationUpdateRequest request);
    CustomerSupportChatResponse startSupportChat(UUID customerId);
    void deleteCustomer(UUID customerId);
}
