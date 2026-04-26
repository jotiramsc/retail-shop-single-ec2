package com.retailshop.service;

import com.retailshop.dto.DailyReportResponse;
import com.retailshop.dto.InvoiceSearchResponse;
import com.retailshop.dto.LowStockProductResponse;

import java.time.LocalDate;
import java.util.List;

public interface ReportService {
    DailyReportResponse getDailyReport(LocalDate fromDate, LocalDate toDate);
    List<LowStockProductResponse> getLowStockProducts();
    InvoiceSearchResponse getInvoices(LocalDate fromDate, LocalDate toDate, String customerName);
}
