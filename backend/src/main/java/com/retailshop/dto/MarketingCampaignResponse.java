package com.retailshop.dto;

import com.retailshop.enums.MarketingCampaignType;
import com.retailshop.enums.MarketingDiscountType;
import com.retailshop.enums.MarketingLanguage;
import com.retailshop.enums.MarketingPlatform;
import com.retailshop.enums.MarketingTone;
import com.retailshop.enums.MarketingWorkflowStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class MarketingCampaignResponse {
    private UUID id;
    private String campaignName;
    private MarketingCampaignType campaignType;
    private String campaignGoal;
    private String offerMode;
    private UUID linkedOfferId;
    private UUID categoryId;
    private UUID productId;
    private String categoryName;
    private String productName;
    private String offerTitle;
    private String landingUrl;
    private String couponCode;
    private MarketingDiscountType discountType;
    private BigDecimal discountValue;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<MarketingPlatform> targetPlatforms;
    private MarketingLanguage language;
    private MarketingTone tone;
    private MarketingWorkflowStatus status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MarketingContentResponse> contents;
}
