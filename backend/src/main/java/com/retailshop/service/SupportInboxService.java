package com.retailshop.service;

import com.retailshop.dto.SupportConversationDetailResponse;
import com.retailshop.dto.SupportConversationSummaryResponse;
import com.retailshop.dto.SupportInboxSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface SupportInboxService {
    SupportInboxSummaryResponse getSummary();

    List<SupportConversationSummaryResponse> listConversations(String status, String search);

    SupportConversationDetailResponse getConversation(UUID conversationId);

    SupportConversationDetailResponse reply(UUID conversationId, String message);

    SupportConversationDetailResponse sendProduct(UUID conversationId, UUID productId);

    SupportConversationDetailResponse markResolved(UUID conversationId);
}
