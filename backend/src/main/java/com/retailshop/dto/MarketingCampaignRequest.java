package com.retailshop.dto;

import com.retailshop.enums.MarketingCampaignType;
import com.retailshop.enums.MarketingDiscountType;
import com.retailshop.enums.MarketingLanguage;
import com.retailshop.enums.MarketingPlatform;
import com.retailshop.enums.MarketingTone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class MarketingCampaignRequest {

    @NotBlank
    private String campaignName;

    @NotNull
    private MarketingCampaignType campaignType;

    private String campaignGoal;
    private String offerMode;
    private UUID offerId;
    private UUID categoryId;
    private UUID productId;
    private String offerTitle;
    private String landingUrl;
    private String couponCode;

    @Valid
    private OfferRequest inlineOffer;

    @NotNull
    private MarketingDiscountType discountType = MarketingDiscountType.NONE;

    private BigDecimal discountValue;
    private LocalDate startDate;
    private LocalDate endDate;

    @NotEmpty
    private Set<MarketingPlatform> targetPlatforms = new LinkedHashSet<>();

    @NotNull
    private MarketingLanguage language = MarketingLanguage.ENGLISH;

    @NotNull
    private MarketingTone tone = MarketingTone.PREMIUM;
}
