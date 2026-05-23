package com.retailshop.dto;

import com.retailshop.enums.OrderSource;
import com.retailshop.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class DashboardAnalyticsResponse {
    private Long totalCustomers;
    private Long customerVisits;
    private BigDecimal totalSales;
    private Long totalOrders;
    private Long pendingOrders;
    private Long completedOrders;
    private Long cancelledOrders;
    private BigDecimal revenueToday;
    private BigDecimal revenueThisMonth;
    private Long lowStockProducts;
    private PercentageChange customerGrowth;
    private PercentageChange visitGrowth;
    private PercentageChange salesGrowth;
    private PercentageChange orderGrowth;
    private PercentageChange todayRevenueGrowth;
    private PercentageChange monthRevenueGrowth;
    private List<TopProduct> topSellingProducts;
    private List<RecentOrder> recentOrders;

    @Getter
    @Builder
    public static class PercentageChange {
        private BigDecimal percentage;
        private String direction;
    }

    @Getter
    @Builder
    public static class TopProduct {
        private UUID productId;
        private String name;
        private String sku;
        private String category;
        private Long quantitySold;
        private BigDecimal revenue;
    }

    @Getter
    @Builder
    public static class RecentOrder {
        private UUID id;
        private String referenceNumber;
        private OrderSource source;
        private OrderStatus status;
        private String paymentStatus;
        private String customerName;
        private String customerMobile;
        private BigDecimal finalAmount;
        private LocalDateTime createdAt;
    }
}
