package com.retailshop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OmnichannelLeadRequest {
    @NotBlank
    private String channel;
    private String externalUserId;
    private String externalThreadId;
    private String customerName;
    private String mobile;
    private String sourceCampaign;
    private String productInterest;
    private String messageText;
    private String rawPayload;

    // n8n/webhook-friendly aliases. The service normalizes these into the canonical fields above.
    private String externalId;
    private String customerHandleOrPhone;
    private String sourceMessageId;
    private String query;
    private String budget;
    private String occasion;
    private String language;
    private String campaignName;
}
