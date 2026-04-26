package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class InvoiceSearchResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private List<InvoiceResponse> invoices;
}
