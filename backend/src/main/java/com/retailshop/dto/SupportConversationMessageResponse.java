package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class SupportConversationMessageResponse {
    private UUID id;
    private String direction;
    private String messageType;
    private String messageText;
    private LocalDateTime createdAt;
}
