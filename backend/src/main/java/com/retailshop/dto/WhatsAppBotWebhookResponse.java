package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class WhatsAppBotWebhookResponse {
    private boolean accepted;
    private boolean sent;
    private String message;
    private UUID leadId;
    private String customerPhone;
    private String replyText;
    private int productCount;
    private String providerMessageId;
    private String errorMessage;
}
