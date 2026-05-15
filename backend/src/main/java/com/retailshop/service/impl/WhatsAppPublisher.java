package com.retailshop.service.impl;

import com.retailshop.entity.Campaign;
import com.retailshop.entity.CampaignContent;
import com.retailshop.entity.Customer;
import com.retailshop.enums.CampaignType;
import com.retailshop.enums.MarketingPlatform;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.SocialPublisher;
import com.retailshop.service.WhatsAppMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WhatsAppPublisher implements SocialPublisher {

    private final WhatsAppMessageService whatsAppMessageService;
    private final CustomerRepository customerRepository;

    @Override
    public MarketingPlatform platform() {
        return MarketingPlatform.WHATSAPP;
    }

    @Override
    public PublishResult publish(CampaignContent content) {
        Campaign campaign = new Campaign();
        campaign.setCampaignName(content.getCampaign().getCampaignName());
        campaign.setName(content.getCampaign().getCampaignName());
        campaign.setType(CampaignType.WHATSAPP);
        campaign.setContent(content.getCaptionText());
        campaign.setHashtags(content.getHashtags());
        campaign.setLinkUrl(content.getCampaign().getLinkUrl());
        campaign.setMediaUrl(content.getImageUrl());

        List<Customer> customers = customerRepository.findAll();
        MarketingChannelResult result = whatsAppMessageService.publishCampaign(campaign, customers);
        return new PublishResult(
                result.isSuccess(),
                result.getResponseId(),
                buildRequestPayload(content),
                safe(result.getDeliveryReport(), result.getResponseId()),
                result.getErrorMessage()
        );
    }

    private String buildRequestPayload(CampaignContent content) {
        Campaign campaign = content.getCampaign();
        return "provider=META"
                + ";platform=" + content.getPlatform().name()
                + ";campaign=" + safe(campaign.getCampaignName())
                + ";landingUrl=" + safe(campaign.getLinkUrl());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safe(String value, String fallback) {
        return safe(value).isEmpty() ? safe(fallback) : safe(value);
    }
}
