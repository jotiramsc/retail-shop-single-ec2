package com.retailshop.service;

import com.retailshop.dto.CustomerRequest;
import com.retailshop.dto.CustomerResponse;
import com.retailshop.dto.CustomerLookupResponse;
import com.retailshop.dto.PurchaseHistoryResponse;
import com.retailshop.entity.Customer;

import java.util.List;

public interface CustomerService {
    CustomerResponse createCustomer(CustomerRequest request);
    List<CustomerResponse> getAllCustomers();
    List<CustomerResponse> searchCustomers(String query);
    List<PurchaseHistoryResponse> getPurchaseHistory(String mobile);
    CustomerLookupResponse lookupCustomer(String mobile);
    Customer findOrCreateCustomer(String name, String mobile);
}
