package com.retailshop.controller;

import com.retailshop.dto.DailyReportResponse;
import com.retailshop.dto.LowStockProductResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.ReportOrderFeedResponse;
import com.retailshop.dto.SalesReportResponse;
import com.retailshop.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_REPORTS')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/daily")
    public DailyReportResponse getDailyReport(@RequestParam(required = false) LocalDate fromDate,
                                              @RequestParam(required = false) LocalDate toDate) {
        return reportService.getDailyReport(fromDate, toDate);
    }

    @GetMapping("/low-stock")
    public PaginatedResponse<LowStockProductResponse> getLowStockProducts(@RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return reportService.getLowStockProducts(pageable);
    }

    @GetMapping("/invoices")
    public ReportOrderFeedResponse getInvoices(@RequestParam(required = false) LocalDate fromDate,
                                               @RequestParam(required = false) LocalDate toDate,
                                               @RequestParam(required = false) String customerName,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return reportService.getOrders(fromDate, toDate, customerName, pageable);
    }

    @GetMapping("/sales")
    public SalesReportResponse getSalesReport(@RequestParam(defaultValue = "MONTHLY") String period,
                                              @RequestParam(required = false) String month,
                                              @RequestParam(required = false) Integer year,
                                              @RequestParam(defaultValue = "ALL") String scope,
                                              @RequestParam(required = false) String category,
                                              @RequestParam(required = false) UUID productId) {
        return reportService.getSalesReport(period, month, year, scope, category, productId);
    }
}
