package com.retailshop.controller;

import com.retailshop.dto.SupportConversationDetailResponse;
import com.retailshop.dto.SupportConversationSummaryResponse;
import com.retailshop.dto.SupportInboxSummaryResponse;
import com.retailshop.dto.SupportReplyRequest;
import com.retailshop.dto.SupportSendProductRequest;
import com.retailshop.service.SupportInboxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OWNER') or hasAuthority('PERM_CUSTOMERS')")
public class SupportInboxController {

    private final SupportInboxService supportInboxService;

    @GetMapping("/summary")
    public SupportInboxSummaryResponse summary() {
        return supportInboxService.getSummary();
    }

    @GetMapping("/conversations")
    public List<SupportConversationSummaryResponse> conversations(@RequestParam(required = false) String status,
                                                                  @RequestParam(required = false) String search) {
        return supportInboxService.listConversations(status, search);
    }

    @GetMapping("/conversations/{conversationId}")
    public SupportConversationDetailResponse conversation(@PathVariable UUID conversationId) {
        return supportInboxService.getConversation(conversationId);
    }

    @PostMapping("/conversations/{conversationId}/reply")
    public SupportConversationDetailResponse reply(@PathVariable UUID conversationId,
                                                   @Valid @RequestBody SupportReplyRequest request) {
        return supportInboxService.reply(conversationId, request.getMessage());
    }

    @PostMapping("/conversations/{conversationId}/send-product")
    public SupportConversationDetailResponse sendProduct(@PathVariable UUID conversationId,
                                                         @Valid @RequestBody SupportSendProductRequest request) {
        return supportInboxService.sendProduct(conversationId, request.getProductId());
    }

    @PatchMapping("/conversations/{conversationId}/resolve")
    public SupportConversationDetailResponse resolve(@PathVariable UUID conversationId) {
        return supportInboxService.markResolved(conversationId);
    }
}
