package com.retailshop.dto;

import com.retailshop.enums.MarketingCampaignType;
import com.retailshop.enums.MarketingDiscountType;
import com.retailshop.enums.MarketingLanguage;
import com.retailshop.enums.MarketingPlatform;
import com.retailshop.enums.MarketingTone;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class MarketingCampaignSuggestionResponse {
    private String key;
    private String kind;
    private String occasionName;
    private String campaignName;
    private String offerTitle;
    private String rationale;
    private String description;
    private String imagePrompt;
    private String templateType;
    private String windowLabel;
    private Integer daysUntil;
    private LocalDate highlightDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private MarketingCampaignType campaignType;
    private MarketingDiscountType discountType;
    private BigDecimal discountValue;
    private MarketingLanguage language;
    private MarketingTone tone;
    private List<MarketingPlatform> targetPlatforms;
    private String landingUrl;
}
