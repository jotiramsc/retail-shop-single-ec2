package com.retailshop.service.impl;

import com.retailshop.dto.DailyReportResponse;
import com.retailshop.dto.LowStockProductResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.ReportOrderFeedResponse;
import com.retailshop.dto.ReportOrderRowResponse;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.Invoice;
import com.retailshop.enums.OrderSource;
import com.retailshop.enums.OrderStatus;
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.repository.InvoiceRepository;
import com.retailshop.service.ProductService;
import com.retailshop.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerOrderRepository customerOrderRepository;
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
        List<Invoice> invoices = invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
        List<CustomerOrder> websiteOrders = customerOrderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end).stream()
                .filter(order -> order.getSource() == OrderSource.WEBSITE)
                .toList();

        return DailyReportResponse.builder()
                .fromDate(rangeStart)
                .toDate(rangeEnd)
                .invoiceCount((long) (invoices.size() + websiteOrders.size()))
                .totalSales(invoices.stream()
                        .map(Invoice::getFinalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .add(websiteOrders.stream()
                                .map(CustomerOrder::getFinalAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .totalDiscount(invoices.stream()
                        .map(Invoice::getDiscount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .add(websiteOrders.stream()
                                .map(CustomerOrder::getDiscount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<LowStockProductResponse> getLowStockProducts(Pageable pageable) {
        return productService.getLowStockProducts(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportOrderFeedResponse getOrders(LocalDate fromDate, LocalDate toDate, String customerName, Pageable pageable) {
        LocalDate rangeEnd = toDate != null ? toDate : LocalDate.now();
        LocalDate rangeStart = fromDate != null ? fromDate : rangeEnd;
        if (rangeStart.isAfter(rangeEnd)) {
            LocalDate swap = rangeStart;
            rangeStart = rangeEnd;
            rangeEnd = swap;
        }
        LocalDateTime start = rangeStart.atStartOfDay();
        LocalDateTime end = rangeEnd.atTime(LocalTime.MAX);
        String normalizedName = customerName == null || customerName.isBlank() ? null : customerName.trim();

        List<Invoice> invoices = normalizedName == null
                ? invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end)
                : invoiceRepository.findByCreatedAtBetweenAndCustomer_NameContainingIgnoreCaseOrderByCreatedAtDesc(start, end, normalizedName);

        List<CustomerOrder> websiteOrders = (normalizedName == null
                ? customerOrderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end)
                : customerOrderRepository.findByCreatedAtBetweenAndCustomer_NameContainingIgnoreCaseOrderByCreatedAtDesc(start, end, normalizedName)).stream()
                .filter(order -> order.getSource() == OrderSource.WEBSITE)
                .toList();

        List<ReportOrderRowResponse> combined = Stream.concat(
                        invoices.stream().map(this::mapInvoiceRow),
                        websiteOrders.stream().map(this::mapWebsiteOrderRow)
                )
                .sorted(Comparator.comparing(ReportOrderRowResponse::getCreatedAt).reversed())
                .toList();

        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int fromIndex = Math.min(page * size, combined.size());
        int toIndex = Math.min(fromIndex + size, combined.size());
        List<ReportOrderRowResponse> pageItems = combined.subList(fromIndex, toIndex);
        int totalPages = combined.isEmpty() ? 0 : (int) Math.ceil((double) combined.size() / size);

        return ReportOrderFeedResponse.builder()
                .fromDate(rangeStart)
                .toDate(rangeEnd)
                .orders(pageItems)
                .page(page)
                .size(size)
                .totalItems(combined.size())
                .totalPages(totalPages)
                .hasNext(page + 1 < totalPages)
                .hasPrevious(page > 0)
                .build();
    }

    private BigDecimal defaultValue(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private ReportOrderRowResponse mapInvoiceRow(Invoice invoice) {
        return ReportOrderRowResponse.builder()
                .id(invoice.getId())
                .referenceNumber(invoice.getInvoiceNumber())
                .source(OrderSource.BILLING)
                .status(OrderStatus.COMPLETED)
                .createdAt(invoice.getCreatedAt())
                .customerName(invoice.getCustomer().getName())
                .customerMobile(invoice.getCustomer().getMobile())
                .paymentMode(invoice.getPaymentMode().name())
                .paymentStatus("PAID")
                .finalAmount(invoice.getFinalAmount())
                .discount(invoice.getDiscount())
                .couponCode(invoice.getCouponCode())
                .build();
    }

    private ReportOrderRowResponse mapWebsiteOrderRow(CustomerOrder order) {
        return ReportOrderRowResponse.builder()
                .id(order.getId())
                .referenceNumber(order.getOrderNumber())
                .source(order.getSource())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .customerName(order.getCustomer().getName())
                .customerMobile(order.getCustomer().getMobile())
                .paymentMode(order.getPaymentGateway())
                .paymentStatus(order.getPaymentStatus())
                .finalAmount(order.getFinalAmount())
                .discount(order.getDiscount())
                .couponCode(order.getCouponCode())
                .build();
    }
}
