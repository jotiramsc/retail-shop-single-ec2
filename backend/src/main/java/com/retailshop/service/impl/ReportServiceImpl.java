package com.retailshop.service.impl;

import com.retailshop.dto.DailyReportResponse;
import com.retailshop.dto.LowStockProductResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.ReportOrderFeedResponse;
import com.retailshop.dto.ReportOrderRowResponse;
import com.retailshop.dto.SalesReportResponse;
import com.retailshop.dto.SalesReportRowResponse;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.Invoice;
import com.retailshop.entity.InvoiceItem;
import com.retailshop.entity.OrderItem;
import com.retailshop.exception.BusinessException;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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

    @Override
    @Transactional(readOnly = true)
    public SalesReportResponse getSalesReport(String period,
                                              String month,
                                              Integer year,
                                              String scope,
                                              String category,
                                              UUID productId) {
        PeriodSelection periodSelection = resolvePeriodSelection(period, month, year);
        SalesScopeFilter scopeFilter = resolveScopeFilter(scope, category, productId);
        LocalDateTime start = periodSelection.fromDate().atStartOfDay();
        LocalDateTime end = periodSelection.toDate().atTime(LocalTime.MAX);

        List<Invoice> invoices = invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
        List<CustomerOrder> websiteOrders = customerOrderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end).stream()
                .filter(order -> order.getSource() == OrderSource.WEBSITE)
                .toList();

        Map<String, SalesAccumulator> rowsByKey = new LinkedHashMap<>();
        long matchedOrderCount = 0;

        for (Invoice invoice : invoices) {
            if (accumulateInvoice(invoice, scopeFilter, rowsByKey)) {
                matchedOrderCount++;
            }
        }

        for (CustomerOrder order : websiteOrders) {
            if (accumulateWebsiteOrder(order, scopeFilter, rowsByKey)) {
                matchedOrderCount++;
            }
        }

        List<SalesReportRowResponse> rows = rowsByKey.values().stream()
                .map(SalesAccumulator::toResponse)
                .sorted(Comparator
                        .comparing(SalesReportRowResponse::getNetSales, Comparator.nullsLast(BigDecimal::compareTo))
                        .reversed()
                        .thenComparing(SalesReportRowResponse::getQuantitySold, Comparator.reverseOrder())
                        .thenComparing(SalesReportRowResponse::getProductName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        long quantitySold = rows.stream().mapToLong(SalesReportRowResponse::getQuantitySold).sum();
        BigDecimal grossSales = rows.stream()
                .map(SalesReportRowResponse::getGrossSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDiscount = rows.stream()
                .map(SalesReportRowResponse::getDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal netSales = rows.stream()
                .map(SalesReportRowResponse::getNetSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return SalesReportResponse.builder()
                .period(periodSelection.period())
                .reportLabel(periodSelection.label())
                .fromDate(periodSelection.fromDate())
                .toDate(periodSelection.toDate())
                .scope(scopeFilter.scope())
                .category(scopeFilter.category())
                .productId(scopeFilter.productId())
                .orderCount(matchedOrderCount)
                .quantitySold(quantitySold)
                .grossSales(grossSales)
                .discount(totalDiscount)
                .netSales(netSales)
                .rows(rows)
                .build();
    }

    private BigDecimal defaultValue(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private boolean accumulateInvoice(Invoice invoice,
                                      SalesScopeFilter filter,
                                      Map<String, SalesAccumulator> rowsByKey) {
        BigDecimal baseDiscountTotal = invoice.getItems().stream()
                .map(InvoiceItem::getDiscount)
                .map(this::money)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extraDiscountTotal = money(invoice.getDiscount()).subtract(baseDiscountTotal);
        if (extraDiscountTotal.compareTo(BigDecimal.ZERO) < 0) {
            extraDiscountTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal totalNetBeforeExtra = invoice.getItems().stream()
                .map(this::invoiceNetBeforeExtraDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean matched = false;
        for (InvoiceItem item : invoice.getItems()) {
            if (!matchesFilter(item.getProduct() != null ? item.getProduct().getId() : null,
                    item.getProduct() != null ? item.getProduct().getCategory() : null,
                    filter)) {
                continue;
            }

            matched = true;
            BigDecimal grossSales = money(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            BigDecimal automaticDiscount = money(item.getDiscount());
            BigDecimal additionalDiscount = proratedDiscount(extraDiscountTotal, invoiceNetBeforeExtraDiscount(item), totalNetBeforeExtra);
            accumulateRow(
                    rowsByKey,
                    item.getProduct() != null ? item.getProduct().getId() : null,
                    item.getProduct() != null ? item.getProduct().getName() : "Unknown product",
                    item.getProduct() != null ? item.getProduct().getCategory() : "",
                    item.getProduct() != null ? item.getProduct().getSku() : "",
                    item.getQuantity(),
                    grossSales,
                    automaticDiscount.add(additionalDiscount).setScale(2, RoundingMode.HALF_UP)
            );
        }
        return matched;
    }

    private boolean accumulateWebsiteOrder(CustomerOrder order,
                                           SalesScopeFilter filter,
                                           Map<String, SalesAccumulator> rowsByKey) {
        BigDecimal totalGross = order.getItems().stream()
                .map(OrderItem::getLineTotal)
                .map(this::money)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderDiscount = money(order.getDiscount());

        boolean matched = false;
        for (OrderItem item : order.getItems()) {
            if (!matchesFilter(item.getProduct() != null ? item.getProduct().getId() : null, item.getCategory(), filter)) {
                continue;
            }

            matched = true;
            BigDecimal grossSales = money(item.getLineTotal());
            BigDecimal allocatedDiscount = proratedDiscount(orderDiscount, grossSales, totalGross);
            accumulateRow(
                    rowsByKey,
                    item.getProduct() != null ? item.getProduct().getId() : null,
                    item.getProductName(),
                    item.getCategory(),
                    item.getSku(),
                    item.getQuantity(),
                    grossSales,
                    allocatedDiscount
            );
        }

        return matched;
    }

    private void accumulateRow(Map<String, SalesAccumulator> rowsByKey,
                               UUID productId,
                               String productName,
                               String category,
                               String sku,
                               int quantity,
                               BigDecimal grossSales,
                               BigDecimal discount) {
        String rowKey = productId != null
                ? productId.toString()
                : (safeText(productName) + "|" + safeText(sku) + "|" + safeText(category)).toUpperCase(Locale.ROOT);
        SalesAccumulator accumulator = rowsByKey.computeIfAbsent(rowKey,
                ignored -> new SalesAccumulator(productId, productName, category, sku));
        accumulator.add(quantity, grossSales, discount);
    }

    private boolean matchesFilter(UUID rowProductId, String rowCategory, SalesScopeFilter filter) {
        return switch (filter.scope()) {
            case "CATEGORY" -> safeText(rowCategory).equalsIgnoreCase(filter.category());
            case "PRODUCT" -> rowProductId != null && rowProductId.equals(filter.productId());
            default -> true;
        };
    }

    private PeriodSelection resolvePeriodSelection(String rawPeriod, String rawMonth, Integer rawYear) {
        String normalizedPeriod = safeText(rawPeriod).isBlank() ? "MONTHLY" : safeText(rawPeriod).toUpperCase(Locale.ROOT);
        return switch (normalizedPeriod) {
            case "MONTHLY" -> {
                YearMonth yearMonth = safeText(rawMonth).isBlank() ? YearMonth.now() : parseMonth(rawMonth);
                yield new PeriodSelection(
                        "MONTHLY",
                        "Monthly sales report for " + yearMonth.getMonth().name().substring(0, 1) + yearMonth.getMonth().name().substring(1).toLowerCase(Locale.ROOT) + " " + yearMonth.getYear(),
                        yearMonth.atDay(1),
                        yearMonth.atEndOfMonth()
                );
            }
            case "ANNUAL" -> {
                int selectedYear = rawYear != null ? rawYear : LocalDate.now().getYear();
                if (selectedYear < 2000 || selectedYear > 9999) {
                    throw new BusinessException("Choose a valid report year");
                }
                yield new PeriodSelection(
                        "ANNUAL",
                        "Annual sales report for " + selectedYear,
                        LocalDate.of(selectedYear, 1, 1),
                        LocalDate.of(selectedYear, 12, 31)
                );
            }
            default -> throw new BusinessException("Report period must be MONTHLY or ANNUAL");
        };
    }

    private SalesScopeFilter resolveScopeFilter(String rawScope, String rawCategory, UUID productId) {
        String normalizedScope = safeText(rawScope).isBlank() ? "ALL" : safeText(rawScope).toUpperCase(Locale.ROOT);
        return switch (normalizedScope) {
            case "ALL" -> new SalesScopeFilter("ALL", null, null);
            case "CATEGORY" -> {
                String normalizedCategory = safeText(rawCategory).trim().toUpperCase(Locale.ROOT);
                if (normalizedCategory.isBlank()) {
                    throw new BusinessException("Choose a category for the category sales report");
                }
                yield new SalesScopeFilter("CATEGORY", normalizedCategory, null);
            }
            case "PRODUCT" -> {
                if (productId == null) {
                    throw new BusinessException("Choose an item for the item sales report");
                }
                yield new SalesScopeFilter("PRODUCT", null, productId);
            }
            default -> throw new BusinessException("Report scope must be ALL, CATEGORY, or PRODUCT");
        };
    }

    private BigDecimal invoiceNetBeforeExtraDiscount(InvoiceItem item) {
        BigDecimal grossSales = money(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        return grossSales.subtract(money(item.getDiscount())).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal proratedDiscount(BigDecimal totalDiscount, BigDecimal subjectAmount, BigDecimal baseAmount) {
        if (totalDiscount.compareTo(BigDecimal.ZERO) <= 0 || baseAmount.compareTo(BigDecimal.ZERO) <= 0 || subjectAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return totalDiscount.multiply(subjectAmount)
                .divide(baseAmount, 2, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private YearMonth parseMonth(String rawMonth) {
        try {
            return YearMonth.parse(rawMonth.trim());
        } catch (RuntimeException exception) {
            throw new BusinessException("Choose a valid month in YYYY-MM format");
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
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

    private record PeriodSelection(String period, String label, LocalDate fromDate, LocalDate toDate) {
    }

    private record SalesScopeFilter(String scope, String category, UUID productId) {
    }

    private static final class SalesAccumulator {
        private final UUID productId;
        private final String productName;
        private final String category;
        private final String sku;
        private long quantitySold;
        private BigDecimal grossSales = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal discount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        private SalesAccumulator(UUID productId, String productName, String category, String sku) {
            this.productId = productId;
            this.productName = productName;
            this.category = category;
            this.sku = sku;
        }

        private void add(int quantity, BigDecimal grossValue, BigDecimal discountValue) {
            quantitySold += quantity;
            grossSales = grossSales.add(grossValue).setScale(2, RoundingMode.HALF_UP);
            discount = discount.add(discountValue).setScale(2, RoundingMode.HALF_UP);
        }

        private SalesReportRowResponse toResponse() {
            return SalesReportRowResponse.builder()
                    .productId(productId)
                    .productName(productName)
                    .category(category)
                    .sku(sku)
                    .quantitySold(quantitySold)
                    .grossSales(grossSales)
                    .discount(discount)
                    .netSales(grossSales.subtract(discount).setScale(2, RoundingMode.HALF_UP))
                    .build();
        }
    }
}
