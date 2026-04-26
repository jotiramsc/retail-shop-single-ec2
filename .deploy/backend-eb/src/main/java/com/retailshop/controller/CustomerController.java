package com.retailshop.controller;

import com.retailshop.dto.CustomerRequest;
import com.retailshop.dto.CustomerResponse;
import com.retailshop.dto.CustomerLookupResponse;
import com.retailshop.dto.PurchaseHistoryResponse;
import com.retailshop.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(@Valid @RequestBody CustomerRequest request) {
        return customerService.createCustomer(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<CustomerResponse> getCustomers() {
        return customerService.getAllCustomers();
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public List<CustomerResponse> searchCustomers(@RequestParam String q) {
        return customerService.searchCustomers(q);
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PurchaseHistoryResponse> getPurchaseHistory(@RequestParam String mobile) {
        return customerService.getPurchaseHistory(mobile);
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public CustomerLookupResponse lookupCustomer(@RequestParam String mobile) {
        return customerService.lookupCustomer(mobile);
    }
}
