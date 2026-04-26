package com.retailshop.service;

import com.retailshop.dto.CustomerRequest;
import com.retailshop.dto.CustomerResponse;
import com.retailshop.dto.CustomerLookupResponse;
import com.retailshop.dto.PurchaseHistoryResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.entity.Customer;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CustomerService {
    CustomerResponse createCustomer(CustomerRequest request);
    PaginatedResponse<CustomerResponse> getAllCustomers(Pageable pageable);
    List<CustomerResponse> searchCustomers(String query);
    List<PurchaseHistoryResponse> getPurchaseHistory(String mobile);
    CustomerLookupResponse lookupCustomer(String mobile);
    Customer findOrCreateCustomer(String name, String mobile);
}
