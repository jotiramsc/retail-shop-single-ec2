package com.retailshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "whatsapp_bot_traces")
public class WhatsAppBotTrace {

    @Id
    private UUID id;

    @Column(name = "stage", nullable = false, length = 50)
    private String stage;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "session_id", length = 80)
    private String sessionId;

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(name = "incoming_message", columnDefinition = "TEXT")
    private String incomingMessage;

    @Column(name = "intent", length = 80)
    private String intent;

    @Column(name = "category", length = 255)
    private String category;

    @Column(name = "search_text", length = 1000)
    private String searchText;

    @Column(name = "min_price", length = 40)
    private String minPrice;

    @Column(name = "max_price", length = 40)
    private String maxPrice;

    @Column(name = "conversation_stage", length = 120)
    private String conversationStage;

    @Column(name = "matched_products", columnDefinition = "TEXT")
    private String matchedProducts;

    @Column(name = "ai_response", columnDefinition = "TEXT")
    private String aiResponse;

    @Column(name = "image_send_started")
    private Boolean imageSendStarted;

    @Column(name = "image_send_result", length = 1000)
    private String imageSendResult;

    @Column(name = "sent")
    private Boolean sent;

    @Column(name = "provider_message_id", length = 1000)
    private String providerMessageId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
