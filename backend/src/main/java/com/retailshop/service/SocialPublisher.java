package com.retailshop.service;

import com.retailshop.entity.CampaignContent;
import com.retailshop.enums.MarketingPlatform;

public interface SocialPublisher {
    MarketingPlatform platform();

    PublishResult publish(CampaignContent content);

    record PublishResult(boolean success, String externalPostId, String requestPayload, String responsePayload, String errorMessage) {
    }
}
