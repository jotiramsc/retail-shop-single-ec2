package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CustomerSupportChatResponse {
    private UUID conversationId;
    private String status;
    private String customerName;
    private String mobile;
}
