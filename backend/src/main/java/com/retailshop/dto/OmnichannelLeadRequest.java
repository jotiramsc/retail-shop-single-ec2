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
}
