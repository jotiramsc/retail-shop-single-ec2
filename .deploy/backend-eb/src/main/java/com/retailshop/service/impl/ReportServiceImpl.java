package com.retailshop.service.impl;

import com.retailshop.dto.DailyReportResponse;
import com.retailshop.dto.InvoiceSearchResponse;
import com.retailshop.dto.LowStockProductResponse;
import com.retailshop.repository.InvoiceRepository;
import com.retailshop.service.BillingService;
import com.retailshop.service.ProductService;
import com.retailshop.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final InvoiceRepository invoiceRepository;
    private final BillingService billingService;
    private final ProductService productService;

    @Override
    @Transactional(readOnly = true)
    public DailyReportResponse getDailyReport(LocalDate fromDate, LocalDate toDate) {
        LocalDate rangeEnd = toDate != null ? toDate : LocalDate.now();
        LocalDate rangeStart = fromDate != null ? fromDate : rangeEnd;
        if (rangeStart.isAfter(rangeEnd)) {
            LocalDate swap = rangeStart;
            rangeStart = rangeEnd;
            rangeEnd = swap;
        }
        LocalDateTime start = rangeStart.atStartOfDay();
        LocalDateTime end = rangeEnd.atTime(LocalTime.MAX);
        return DailyReportResponse.builder()
                .fromDate(rangeStart)
                .toDate(rangeEnd)
                .invoiceCount(invoiceRepository.countByCreatedAtBetween(start, end))
                .totalSales(defaultValue(invoiceRepository.sumFinalAmountBetween(start, end)))
                .totalDiscount(defaultValue(invoiceRepository.sumDiscountBetween(start, end)))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LowStockProductResponse> getLowStockProducts() {
        return productService.getLowStockProducts();
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceSearchResponse getInvoices(LocalDate fromDate, LocalDate toDate, String customerName) {
        return billingService.searchInvoices(fromDate, toDate, customerName);
    }

    private BigDecimal defaultValue(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
