package com.retailshop.controller;

import com.retailshop.dto.WhatsAppBotTraceResponse;
import com.retailshop.entity.WhatsAppBotTrace;
import com.retailshop.repository.WhatsAppBotTraceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/whatsapp/bot-traces")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OWNER') or hasAuthority('PERM_CUSTOMERS')")
public class WhatsAppBotTraceController {

    private final WhatsAppBotTraceRepository traceRepository;

    @GetMapping
    public List<WhatsAppBotTraceResponse> traces(@RequestParam(defaultValue = "50") int size,
                                                 @RequestParam(required = false) Boolean failedOnly,
                                                 @RequestParam(required = false) String sessionId) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(size, 1), 200));
        if (sessionId != null && !sessionId.isBlank()) {
            return traceRepository.findBySessionIdOrderByCreatedAtDesc(sessionId.trim(), page).stream().map(this::toResponse).toList();
        }
        if (Boolean.TRUE.equals(failedOnly)) {
            return traceRepository.findBySentFalseOrderByCreatedAtDesc(page).stream().map(this::toResponse).toList();
        }
        return traceRepository.findAllByOrderByCreatedAtDesc(page).stream().map(this::toResponse).toList();
    }

    private WhatsAppBotTraceResponse toResponse(WhatsAppBotTrace trace) {
        return WhatsAppBotTraceResponse.builder()
                .id(trace.getId())
                .stage(trace.getStage())
                .correlationId(trace.getCorrelationId())
                .leadId(trace.getLeadId())
                .sessionId(trace.getSessionId())
                .messageId(trace.getMessageId())
                .incomingMessage(trace.getIncomingMessage())
                .intent(trace.getIntent())
                .category(trace.getCategory())
                .searchText(trace.getSearchText())
                .minPrice(trace.getMinPrice())
                .maxPrice(trace.getMaxPrice())
                .conversationStage(trace.getConversationStage())
                .matchedProducts(trace.getMatchedProducts())
                .aiResponse(trace.getAiResponse())
                .imageSendStarted(trace.getImageSendStarted())
                .imageSendResult(trace.getImageSendResult())
                .sent(trace.getSent())
                .providerMessageId(trace.getProviderMessageId())
                .failureReason(trace.getFailureReason())
                .createdAt(trace.getCreatedAt())
                .build();
    }
}
