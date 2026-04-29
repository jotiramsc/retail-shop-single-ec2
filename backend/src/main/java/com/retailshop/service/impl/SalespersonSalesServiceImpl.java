package com.retailshop.service.impl;

import com.retailshop.dto.SalespersonSalesRecordResponse;
import com.retailshop.dto.SalespersonSalesResponse;
import com.retailshop.dto.SalespersonSalesTrendRowResponse;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.Invoice;
import com.retailshop.entity.StaffUser;
import com.retailshop.enums.OrderSource;
import com.retailshop.enums.StaffRole;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.repository.InvoiceRepository;
import com.retailshop.service.SalespersonSalesService;
import com.retailshop.service.StaffUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalespersonSalesServiceImpl implements SalespersonSalesService {

    private static final DateTimeFormatter DAILY_LABEL_FORMAT = DateTimeFormatter.ofPattern("dd MMM");
    private static final DateTimeFormatter MONTHLY_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy");

    private final InvoiceRepository invoiceRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final StaffUserService staffUserService;

    @Override
    @Transactional(readOnly = true)
    public SalespersonSalesResponse getSalespersonSales(StaffUser viewer,
                                                        String salespersonId,
                                                        LocalDate fromDate,
                                                        LocalDate toDate,
                                                        String viewType) {
        LocalDate resolvedToDate = toDate != null ? toDate : LocalDate.now();
        LocalDate resolvedFromDate = fromDate != null ? fromDate : resolvedToDate.withDayOfMonth(1);
        if (resolvedFromDate.isAfter(resolvedToDate)) {
            throw new BusinessException("From date cannot be after to date");
        }

        String normalizedViewType = normalizeViewType(viewType);
        SalespersonSelection selection = resolveSelection(viewer, salespersonId);

        LocalDateTime start = resolvedFromDate.atStartOfDay();
        LocalDateTime end = resolvedToDate.atTime(23, 59, 59);

        List<SalesRecord> records = new ArrayList<>();

        List<Invoice> invoices = invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
        invoices.stream()
                .filter(invoice -> matchesInvoiceSelection(invoice, selection))
                .map(this::mapInvoiceRecord)
                .forEach(records::add);

        List<CustomerOrder> websiteOrders = customerOrderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
        websiteOrders.stream()
                .filter(order -> order.getSource() == OrderSource.WEBSITE)
                .filter(order -> matchesWebsiteSelection(order, selection))
                .map(this::mapWebsiteRecord)
                .forEach(records::add);

        records.sort(Comparator.comparing(SalesRecord::date).reversed());

        BigDecimal totalSalesAmount = records.stream()
                .map(SalesRecord::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        long totalOrders = records.size();
        long totalItemsSold = records.stream().mapToLong(SalesRecord::itemsSold).sum();
        BigDecimal averageOrderValue = totalOrders == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalSalesAmount.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);

        Map<String, TrendAccumulator> trend = new TreeMap<>();
        for (SalesRecord record : records) {
            String key = trendKey(record.date(), normalizedViewType);
            trend.computeIfAbsent(key, ignored -> new TrendAccumulator())
                    .add(record.totalAmount(), record.itemsSold());
        }

        List<SalespersonSalesTrendRowResponse> trendRows = trend.entrySet().stream()
                .map(entry -> SalespersonSalesTrendRowResponse.builder()
                        .label(formatTrendLabel(entry.getKey(), normalizedViewType))
                        .orderCount(entry.getValue().orderCount)
                        .itemsSold(entry.getValue().itemsSold)
                        .totalSalesAmount(entry.getValue().totalSalesAmount.setScale(2, RoundingMode.HALF_UP))
                        .build())
                .toList();

        List<SalespersonSalesRecordResponse> tableRows = records.stream()
                .map(record -> SalespersonSalesRecordResponse.builder()
                        .id(record.id())
                        .date(record.date())
                        .billNo(record.billNo())
                        .customerName(record.customerName())
                        .salespersonName(record.salespersonName())
                        .totalAmount(record.totalAmount().setScale(2, RoundingMode.HALF_UP))
                        .paymentMode(record.paymentMode())
                        .build())
                .toList();

        return SalespersonSalesResponse.builder()
                .fromDate(resolvedFromDate)
                .toDate(resolvedToDate)
                .viewType(normalizedViewType)
                .lockedToCurrentUser(selection.lockedToCurrentUser())
                .salespersonId(selection.salespersonId())
                .salespersonName(selection.salespersonName())
                .totalSalesAmount(totalSalesAmount)
                .totalOrders(totalOrders)
                .totalItemsSold(totalItemsSold)
                .averageOrderValue(averageOrderValue)
                .trend(trendRows)
                .records(tableRows)
                .build();
    }

    private SalespersonSelection resolveSelection(StaffUser viewer, String salespersonId) {
        if (viewer.getRole() != StaffRole.ADMIN) {
            return new SalespersonSelection(
                    viewer.getId().toString(),
                    viewer.getId(),
                    viewer.getDisplayName(),
                    SalespersonSelectionType.USER,
                    true
            );
        }

        String normalizedId = safeText(salespersonId);
        if (normalizedId == null) {
            return new SalespersonSelection(null, null, "All salespersons", SalespersonSelectionType.ALL, false);
        }
        if ("WEBSITE".equalsIgnoreCase(normalizedId)) {
            return new SalespersonSelection("WEBSITE", null, "Website", SalespersonSelectionType.WEBSITE, false);
        }

        try {
            UUID userId = UUID.fromString(normalizedId);
            StaffUser salesPerson = staffUserService.getActiveSalesPerson(userId);
            return new SalespersonSelection(
                    salesPerson.getId().toString(),
                    salesPerson.getId(),
                    salesPerson.getDisplayName(),
                    SalespersonSelectionType.USER,
                    false
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("Invalid salesperson selection");
        }
    }

    private boolean matchesInvoiceSelection(Invoice invoice, SalespersonSelection selection) {
        return switch (selection.type()) {
            case ALL -> true;
            case WEBSITE -> false;
            case USER -> selection.userId() != null && selection.userId().equals(invoice.getSalesPersonUserId());
        };
    }

    private boolean matchesWebsiteSelection(CustomerOrder order, SalespersonSelection selection) {
        return switch (selection.type()) {
            case ALL -> true;
            case WEBSITE -> true;
            case USER -> selection.userId() != null && selection.userId().equals(order.getSalesPersonUserId());
        };
    }

    private SalesRecord mapInvoiceRecord(Invoice invoice) {
        long itemsSold = invoice.getItems().stream().mapToLong(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum();
        return new SalesRecord(
                invoice.getId(),
                invoice.getCreatedAt(),
                invoice.getInvoiceNumber(),
                invoice.getCustomer() != null ? invoice.getCustomer().getName() : "Walk-in Customer",
                safeText(invoice.getSalesPersonName(), "Unassigned"),
                money(invoice.getFinalAmount()),
                invoice.getPaymentMode() != null ? invoice.getPaymentMode().name() : "CASH",
                itemsSold
        );
    }

    private SalesRecord mapWebsiteRecord(CustomerOrder order) {
        long itemsSold = order.getItems().stream().mapToLong(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum();
        String paymentMode = safeText(order.getPaymentGateway());
        if (paymentMode == null) {
            paymentMode = "ONLINE";
        }
        return new SalesRecord(
                order.getId(),
                order.getCreatedAt(),
                order.getOrderNumber(),
                order.getCustomer() != null ? order.getCustomer().getName() : "Website Customer",
                safeText(order.getSalesPersonName(), "Website"),
                money(order.getFinalAmount()),
                paymentMode,
                itemsSold
        );
    }

    private String normalizeViewType(String viewType) {
        String normalized = safeText(viewType, "DAILY");
        return switch (normalized.toUpperCase()) {
            case "DAILY", "MONTHLY", "YEARLY" -> normalized.toUpperCase();
            default -> throw new BusinessException("Unsupported view type");
        };
    }

    private String trendKey(LocalDateTime createdAt, String viewType) {
        return switch (viewType) {
            case "MONTHLY" -> YearMonth.from(createdAt).toString();
            case "YEARLY" -> String.valueOf(createdAt.getYear());
            default -> createdAt.toLocalDate().toString();
        };
    }

    private String formatTrendLabel(String key, String viewType) {
        return switch (viewType) {
            case "MONTHLY" -> YearMonth.parse(key).atDay(1).format(MONTHLY_LABEL_FORMAT);
            case "YEARLY" -> key;
            default -> LocalDate.parse(key).format(DAILY_LABEL_FORMAT);
        };
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeText(String value, String fallback) {
        String normalized = safeText(value);
        return normalized != null ? normalized : fallback;
    }

    private enum SalespersonSelectionType {
        ALL,
        USER,
        WEBSITE
    }

    private record SalespersonSelection(String salespersonId,
                                        UUID userId,
                                        String salespersonName,
                                        SalespersonSelectionType type,
                                        boolean lockedToCurrentUser) {
    }

    private record SalesRecord(UUID id,
                               LocalDateTime date,
                               String billNo,
                               String customerName,
                               String salespersonName,
                               BigDecimal totalAmount,
                               String paymentMode,
                               long itemsSold) {
    }

    private static final class TrendAccumulator {
        private long orderCount;
        private long itemsSold;
        private BigDecimal totalSalesAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        void add(BigDecimal totalAmount, long itemsSold) {
            this.orderCount++;
            this.itemsSold += itemsSold;
            this.totalSalesAmount = this.totalSalesAmount.add(totalAmount);
        }
    }
}
