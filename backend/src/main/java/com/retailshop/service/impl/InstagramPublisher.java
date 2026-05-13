package com.retailshop.service.impl;

import com.retailshop.entity.Campaign;
import com.retailshop.entity.CampaignContent;
import com.retailshop.enums.CampaignType;
import com.retailshop.enums.MarketingPlatform;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.SocialMediaService;
import com.retailshop.service.SocialPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InstagramPublisher implements SocialPublisher {

    private final SocialMediaService socialMediaService;

    @Override
    public MarketingPlatform platform() {
        return MarketingPlatform.INSTAGRAM;
    }

    @Override
    public PublishResult publish(CampaignContent content) {
        Campaign campaign = buildLegacyCampaign(content, CampaignType.INSTAGRAM);
        MarketingChannelResult result = socialMediaService.publishInstagram(campaign);
        return new PublishResult(result.isSuccess(), result.getResponseId(), buildRequestPayload(content), result.getResponseId(), result.getErrorMessage());
    }

    private Campaign buildLegacyCampaign(CampaignContent content, CampaignType type) {
        Campaign campaign = new Campaign();
        campaign.setCampaignName(content.getCampaign().getCampaignName());
        campaign.setName(content.getCampaign().getCampaignName());
        campaign.setType(type);
        campaign.setContent(buildPublishContent(content));
        campaign.setHashtags(content.getHashtags());
        campaign.setMediaUrl(content.getImageUrl());
        campaign.setLinkUrl(resolveLandingUrl(content));
        campaign.setLanguage(content.getCampaign().getLanguage());
        return campaign;
    }

    private String buildRequestPayload(CampaignContent content) {
        return "caption=" + safe(content.getCaptionText()) + ";hashtags=" + safe(content.getHashtags()) + ";imageUrl=" + safe(content.getImageUrl());
    }

    private String buildPublishContent(CampaignContent content) {
        String caption = safe(content.getCaptionText());
        String cta = safe(content.getCallToAction());
        if (cta.isBlank()) {
            return caption;
        }
        if (!caption.isBlank() && caption.toLowerCase().contains(cta.toLowerCase())) {
            return caption;
        }
        return caption.isBlank() ? cta : caption + "\n\n" + cta;
    }

    private String resolveLandingUrl(CampaignContent content) {
        String url = safe(content.getCampaign().getLinkUrl());
        return url.isBlank() ? "https://kpskrishnai.com" : url;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
