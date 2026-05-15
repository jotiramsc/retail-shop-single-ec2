package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class SupportConversationDetailResponse {
    private UUID id;
    private String customerName;
    private String phone;
    private String status;
    private long unreadCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SupportConversationMessageResponse> messages;
}
