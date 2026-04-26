package com.retailshop.controller;

import com.retailshop.dto.InvoiceCreateRequest;
import com.retailshop.dto.InvoiceResponse;
import com.retailshop.dto.InvoiceSearchResponse;
import com.retailshop.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_BILLING')")
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/preview")
    public InvoiceResponse previewInvoice(@Valid @RequestBody InvoiceCreateRequest request) {
        return billingService.previewInvoice(request);
    }

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse createInvoice(@Valid @RequestBody InvoiceCreateRequest request) {
        return billingService.createInvoice(request);
    }

    @PutMapping("/{id}")
    public InvoiceResponse updateInvoice(@PathVariable UUID id, @Valid @RequestBody InvoiceCreateRequest request) {
        return billingService.updateInvoice(id, request);
    }

    @GetMapping("/{id}")
    public InvoiceResponse getInvoice(@PathVariable UUID id) {
        return billingService.getInvoice(id);
    }

    @GetMapping("/search")
    public InvoiceSearchResponse searchInvoices(@RequestParam(required = false) LocalDate fromDate,
                                                @RequestParam(required = false) LocalDate toDate,
                                                @RequestParam(required = false) String customerName,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return billingService.searchInvoices(fromDate, toDate, customerName, pageable);
    }
}
