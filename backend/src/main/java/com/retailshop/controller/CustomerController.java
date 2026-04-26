package com.retailshop.controller;

import com.retailshop.dto.CustomerRequest;
import com.retailshop.dto.CustomerResponse;
import com.retailshop.dto.CustomerLookupResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.PurchaseHistoryResponse;
import com.retailshop.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    @PreAuthorize("hasAuthority('PERM_CUSTOMERS')")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(@Valid @RequestBody CustomerRequest request) {
        return customerService.createCustomer(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_CUSTOMERS')")
    public PaginatedResponse<CustomerResponse> getCustomers(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return customerService.getAllCustomers(pageable);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('PERM_CUSTOMERS', 'PERM_BILLING', 'PERM_REPORTS')")
    public List<CustomerResponse> searchCustomers(@RequestParam String q) {
        return customerService.searchCustomers(q);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('PERM_CUSTOMERS')")
    public List<PurchaseHistoryResponse> getPurchaseHistory(@RequestParam String mobile) {
        return customerService.getPurchaseHistory(mobile);
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('PERM_CUSTOMERS', 'PERM_BILLING')")
    public CustomerLookupResponse lookupCustomer(@RequestParam String mobile) {
        return customerService.lookupCustomer(mobile);
    }
}
