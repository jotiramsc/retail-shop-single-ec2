package com.retailshop.service;

import com.retailshop.dto.InvoiceCreateRequest;
import com.retailshop.dto.InvoiceResponse;
import com.retailshop.dto.InvoiceSearchResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface BillingService {
    InvoiceResponse previewInvoice(InvoiceCreateRequest request);
    InvoiceResponse createInvoice(InvoiceCreateRequest request);
    InvoiceResponse updateInvoice(UUID id, InvoiceCreateRequest request);
    InvoiceResponse getInvoice(UUID id);
    InvoiceSearchResponse searchInvoices(LocalDate fromDate, LocalDate toDate, String customerName, Pageable pageable);
}
