package com.retailshop.service.impl;

import com.retailshop.dto.DashboardAnalyticsResponse;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.Invoice;
import com.retailshop.entity.InvoiceItem;
import com.retailshop.entity.OrderItem;
import com.retailshop.entity.Product;
import com.retailshop.enums.OrderSource;
import com.retailshop.enums.OrderStatus;
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.InvoiceRepository;
import com.retailshop.repository.OrderItemRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.repository.SiteVisitRepository;
import com.retailshop.service.DashboardAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardAnalyticsServiceImpl implements DashboardAnalyticsService {

    private final CustomerRepository customerRepository;
    private final SiteVisitRepository siteVisitRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final ProductRepository productRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardAnalyticsResponse getDashboardAnalytics() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();
        LocalDateTime yesterdayEnd = today.minusDays(1).atTime(LocalTime.MAX);
        YearMonth currentMonth = YearMonth.from(today);
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.atEndOfMonth().atTime(LocalTime.MAX);
        YearMonth previousMonth = currentMonth.minusMonths(1);
        LocalDateTime previousMonthStart = previousMonth.atDay(1).atStartOfDay();
        LocalDateTime previousMonthEnd = previousMonth.atEndOfMonth().atTime(LocalTime.MAX);

        BigDecimal totalSales = sum(invoiceRepository.sumFinalAmount(), customerOrderRepository.sumFinalAmount());
        BigDecimal revenueToday = revenueBetween(todayStart, todayEnd);
        BigDecimal revenueYesterday = revenueBetween(yesterdayStart, yesterdayEnd);
        BigDecimal revenueThisMonth = revenueBetween(monthStart, monthEnd);
        BigDecimal revenuePreviousMonth = revenueBetween(previousMonthStart, previousMonthEnd);

        long totalOrders = invoiceRepository.count() + customerOrderRepository.count();
        long todayOrders = invoiceRepository.countByCreatedAtBetween(todayStart, todayEnd)
                + customerOrderRepository.countByCreatedAtBetween(todayStart, todayEnd);
        long yesterdayOrders = invoiceRepository.countByCreatedAtBetween(yesterdayStart, yesterdayEnd)
                + customerOrderRepository.countByCreatedAtBetween(yesterdayStart, yesterdayEnd);
        long completedOrders = invoiceRepository.count() + customerOrderRepository.countByStatusIn(EnumSet.of(OrderStatus.COMPLETED, OrderStatus.DELIVERED));
        long pendingOrders = customerOrderRepository.countByStatusIn(EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPED));
        long cancelledOrders = customerOrderRepository.countByStatusIn(EnumSet.of(
                OrderStatus.CANCELLED,
                OrderStatus.RETURNED,
                OrderStatus.REFUND_INITIATED,
                OrderStatus.PAYMENT_FAILED
        ));

        long visitsThisMonth = siteVisitRepository.countByCreatedAtBetween(monthStart, monthEnd);
        long visitsPreviousMonth = siteVisitRepository.countByCreatedAtBetween(previousMonthStart, previousMonthEnd);
        long customersThisMonth = customerRepository.countByCreatedAtBetween(monthStart, monthEnd);
        long customersPreviousMonth = customerRepository.countByCreatedAtBetween(previousMonthStart, previousMonthEnd);

        return DashboardAnalyticsResponse.builder()
                .totalCustomers(customerRepository.count())
                .customerVisits(visitsThisMonth)
                .totalSales(totalSales)
                .totalOrders(totalOrders)
                .pendingOrders(pendingOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .revenueToday(revenueToday)
                .revenueThisMonth(revenueThisMonth)
                .lowStockProducts(productRepository.countLowStockProducts())
                .customerGrowth(change(customersThisMonth, customersPreviousMonth))
                .visitGrowth(change(visitsThisMonth, visitsPreviousMonth))
                .salesGrowth(change(revenueThisMonth, revenuePreviousMonth))
                .orderGrowth(change(todayOrders, yesterdayOrders))
                .todayRevenueGrowth(change(revenueToday, revenueYesterday))
                .monthRevenueGrowth(change(revenueThisMonth, revenuePreviousMonth))
                .topSellingProducts(topSellingProducts())
                .recentOrders(recentOrders())
                .build();
    }

    private BigDecimal revenueBetween(LocalDateTime start, LocalDateTime end) {
        return sum(invoiceRepository.sumFinalAmountBetween(start, end), customerOrderRepository.sumFinalAmountBetween(start, end));
    }

    private List<DashboardAnalyticsResponse.TopProduct> topSellingProducts() {
        Map<UUID, ProductSales> salesByProduct = new LinkedHashMap<>();
        for (InvoiceItem item : invoiceItemRepository.findAll()) {
            if (item.getProduct() == null) continue;
            UUID productId = item.getProduct().getId();
            salesByProduct.computeIfAbsent(productId, ignored -> new ProductSales(item.getProduct()))
                    .add(item.getQuantity(), item.getPrice());
        }
        for (OrderItem item : orderItemRepository.findAll()) {
            if (item.getProduct() == null || item.getOrder() == null || item.getOrder().getStatus() == OrderStatus.CANCELLED) continue;
            UUID productId = item.getProduct().getId();
            salesByProduct.computeIfAbsent(productId, ignored -> new ProductSales(item.getProduct()))
                    .add(item.getQuantity(), item.getPrice());
        }
        return salesByProduct.values().stream()
                .sorted(Comparator.comparing(ProductSales::quantitySold).reversed())
                .limit(5)
                .map(ProductSales::toResponse)
                .toList();
    }

    private List<DashboardAnalyticsResponse.RecentOrder> recentOrders() {
        List<DashboardAnalyticsResponse.RecentOrder> rows = new ArrayList<>();
        for (Invoice invoice : invoiceRepository.findTop10ByOrderByCreatedAtDesc()) {
            rows.add(DashboardAnalyticsResponse.RecentOrder.builder()
                    .id(invoice.getId())
                    .referenceNumber(invoice.getInvoiceNumber())
                    .source(OrderSource.BILLING)
                    .status(OrderStatus.COMPLETED)
                    .paymentStatus(invoice.getPaymentMode() == null ? "PAID" : invoice.getPaymentMode().name())
                    .customerName(invoice.getCustomer() == null ? null : invoice.getCustomer().getName())
                    .customerMobile(invoice.getCustomer() == null ? null : invoice.getCustomer().getMobile())
                    .finalAmount(invoice.getFinalAmount())
                    .createdAt(invoice.getCreatedAt())
                    .build());
        }
        for (CustomerOrder order : customerOrderRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10))) {
            rows.add(DashboardAnalyticsResponse.RecentOrder.builder()
                    .id(order.getId())
                    .referenceNumber(order.getOrderNumber())
                    .source(order.getSource())
                    .status(order.getStatus())
                    .paymentStatus(order.getPaymentStatus())
                    .customerName(order.getCustomer() == null ? null : order.getCustomer().getName())
                    .customerMobile(order.getCustomer() == null ? null : order.getCustomer().getMobile())
                    .finalAmount(order.getFinalAmount())
                    .createdAt(order.getCreatedAt())
                    .build());
        }
        return rows.stream()
                .sorted(Comparator.comparing(DashboardAnalyticsResponse.RecentOrder::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(8)
                .toList();
    }

    private DashboardAnalyticsResponse.PercentageChange change(long current, long previous) {
        return change(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    private DashboardAnalyticsResponse.PercentageChange change(BigDecimal current, BigDecimal previous) {
        BigDecimal safeCurrent = current == null ? BigDecimal.ZERO : current;
        BigDecimal safePrevious = previous == null ? BigDecimal.ZERO : previous;
        if (safePrevious.compareTo(BigDecimal.ZERO) == 0) {
            return DashboardAnalyticsResponse.PercentageChange.builder()
                    .percentage(safeCurrent.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO)
                    .direction(safeCurrent.compareTo(BigDecimal.ZERO) > 0 ? "up" : "flat")
                    .build();
        }
        BigDecimal delta = safeCurrent.subtract(safePrevious);
        return DashboardAnalyticsResponse.PercentageChange.builder()
                .percentage(delta.multiply(BigDecimal.valueOf(100)).divide(safePrevious.abs(), 1, RoundingMode.HALF_UP))
                .direction(delta.signum() > 0 ? "up" : delta.signum() < 0 ? "down" : "flat")
                .build();
    }

    private BigDecimal sum(BigDecimal left, BigDecimal right) {
        return (left == null ? BigDecimal.ZERO : left).add(right == null ? BigDecimal.ZERO : right);
    }

    private static class ProductSales {
        private final Product product;
        private long quantitySold;
        private BigDecimal revenue = BigDecimal.ZERO;

        private ProductSales(Product product) {
            this.product = product;
        }

        private void add(Integer quantity, BigDecimal price) {
            long safeQuantity = quantity == null ? 0L : quantity.longValue();
            quantitySold += safeQuantity;
            revenue = revenue.add((price == null ? BigDecimal.ZERO : price).multiply(BigDecimal.valueOf(safeQuantity)));
        }

        private Long quantitySold() {
            return quantitySold;
        }

        private DashboardAnalyticsResponse.TopProduct toResponse() {
            return DashboardAnalyticsResponse.TopProduct.builder()
                    .productId(product.getId())
                    .name(product.getName())
                    .sku(product.getSku())
                    .category(product.getCategory())
                    .quantitySold(quantitySold)
                    .revenue(revenue)
                    .build();
        }
    }
}
