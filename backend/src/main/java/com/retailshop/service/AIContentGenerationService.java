package com.retailshop.service;

import com.retailshop.entity.Campaign;
import com.retailshop.enums.MarketingPlatform;

public interface AIContentGenerationService {
    GeneratedMarketingDraft generateDraft(Campaign campaign, String shopName, String categoryName, String productName, MarketingPlatform platform);

    record GeneratedMarketingDraft(
            String captionText,
            String hashtags,
            String callToAction,
            String imagePrompt,
            String imageUrl
    ) {
    }
}
