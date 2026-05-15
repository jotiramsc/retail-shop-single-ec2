package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class SupportConversationSummaryResponse {
    private UUID id;
    private String customerName;
    private String phone;
    private String status;
    private String latestMessage;
    private long unreadCount;
    private LocalDateTime updatedAt;
}
