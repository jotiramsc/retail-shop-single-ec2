package com.retailshop.service;

import com.retailshop.dto.DailyReportResponse;
import com.retailshop.dto.LowStockProductResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.ReportOrderFeedResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface ReportService {
    DailyReportResponse getDailyReport(LocalDate fromDate, LocalDate toDate);
    PaginatedResponse<LowStockProductResponse> getLowStockProducts(Pageable pageable);
    ReportOrderFeedResponse getOrders(LocalDate fromDate, LocalDate toDate, String customerName, Pageable pageable);
}
