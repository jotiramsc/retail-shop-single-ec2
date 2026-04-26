package com.retailshop.controller;

import com.retailshop.dto.DailyReportResponse;
import com.retailshop.dto.InvoiceSearchResponse;
import com.retailshop.dto.LowStockProductResponse;
import com.retailshop.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/daily")
    public DailyReportResponse getDailyReport(@RequestParam(required = false) LocalDate fromDate,
                                              @RequestParam(required = false) LocalDate toDate) {
        return reportService.getDailyReport(fromDate, toDate);
    }

    @GetMapping("/low-stock")
    public List<LowStockProductResponse> getLowStockProducts() {
        return reportService.getLowStockProducts();
    }

    @GetMapping("/invoices")
    public InvoiceSearchResponse getInvoices(@RequestParam(required = false) LocalDate fromDate,
                                             @RequestParam(required = false) LocalDate toDate,
                                             @RequestParam(required = false) String customerName) {
        return reportService.getInvoices(fromDate, toDate, customerName);
    }
}
