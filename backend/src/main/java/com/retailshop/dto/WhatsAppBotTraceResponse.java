package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class WhatsAppBotTraceResponse {
    private UUID id;
    private String stage;
    private String correlationId;
    private UUID leadId;
    private String sessionId;
    private String messageId;
    private String incomingMessage;
    private String intent;
    private String category;
    private String searchText;
    private String minPrice;
    private String maxPrice;
    private String conversationStage;
    private String matchedProducts;
    private String aiResponse;
    private Boolean imageSendStarted;
    private String imageSendResult;
    private Boolean sent;
    private String providerMessageId;
    private String failureReason;
    private LocalDateTime createdAt;
}
