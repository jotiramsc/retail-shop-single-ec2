package com.retailshop.service;

import com.retailshop.entity.Campaign;
import com.retailshop.enums.MarketingPlatform;

public interface AIContentGenerationService {
    GeneratedMarketingDraft generateDraft(Campaign campaign, String shopName, String categoryName, String productName, MarketingPlatform platform);

    GeneratedCreativeImage generateSharedCreativeImage(Campaign campaign,
                                                       String shopName,
                                                       String categoryName,
                                                       String productName,
                                                       String visualSeed);

    record GeneratedMarketingDraft(
            String captionText,
            String hashtags,
            String callToAction,
            String imagePrompt,
            String imageUrl
    ) {
    }

    record GeneratedCreativeImage(
            String imagePrompt,
            String imageUrl
    ) {
    }
}
