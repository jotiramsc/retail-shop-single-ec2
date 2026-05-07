package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class OmnichannelWebhookResponse {
    private boolean accepted;
    private String message;
    private UUID leadId;
}
