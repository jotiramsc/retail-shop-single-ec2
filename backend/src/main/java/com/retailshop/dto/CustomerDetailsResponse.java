package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CustomerDetailsResponse {
    private UUID id;
    private String name;
    private String mobile;
    private String email;
    private String gender;
    private String fullAddress;
    private LocalDateTime customerSince;
    private LocalDate dateOfBirth;
    private LocalDate anniversaryDate;
    private String spouseName;
    private String preferredLanguage;
    private String preferredCategories;
    private String preferredProducts;
    private String preferredBrands;
    private String preferredPriceRange;
    private String shoppingInterests;
    private String customerNotes;
    private String customerTags;
    private String verificationStatus;
    private String customerSource;
    private Boolean loginEnabled;
    private LocalDateTime otpVerifiedAt;
    private boolean birthdayReminderEnabled;
    private boolean anniversaryReminderEnabled;
    private LocalDateTime lastLoginAt;
    private String lastLoginMethod;
    private LocalDateTime lastActiveAt;
    private String lastKnownLocation;
    private String supportChatStatus;
    private LocalDateTime lastOrderDate;
    private long totalOrders;
    private long pendingOrders;
    private BigDecimal totalSpent;
    private List<OrderSummary> orderHistory;
    private List<LoginSummary> loginHistory;
    private List<LocationSummary> locationHistory;
    private List<ActivitySummary> activityHistory;
    private List<ActivitySummary> searchHistory;
    private List<String> preferenceInsights;
    private List<String> segments;
    private List<TimelineEvent> timeline;
    private String customerSentiment;
    private String purchasePrediction;
    private String churnRisk;
    private Integer engagementScore;
    private List<String> recommendedProducts;
    private String highValueBadge;

    @Getter
    @Builder
    public static class OrderSummary {
        private UUID id;
        private String orderNumber;
        private LocalDateTime createdAt;
        private BigDecimal amount;
        private String status;
    }

    @Getter
    @Builder
    public static class LoginSummary {
        private LocalDateTime loginTime;
        private String loginMethod;
        private String device;
        private String browser;
        private String ip;
        private String location;
        private String status;
    }

    @Getter
    @Builder
    public static class LocationSummary {
        private Double latitude;
        private Double longitude;
        private String city;
        private String state;
        private String country;
        private String pincode;
        private Double accuracyMeters;
        private String locationSource;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class ActivitySummary {
        private LocalDateTime createdAt;
        private String activityType;
        private String searchKeyword;
        private String category;
        private String filterUsed;
        private String priceRange;
        private UUID productId;
        private String productName;
        private Integer resultCount;
        private String clickedProduct;
        private Integer timeSpentSeconds;
        private String campaignSource;
        private String page;
    }

    @Getter
    @Builder
    public static class TimelineEvent {
        private LocalDateTime createdAt;
        private String type;
        private String title;
        private String detail;
    }
}
