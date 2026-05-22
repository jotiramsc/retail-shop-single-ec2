package com.retailshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "omnichannel_conversation_messages")
public class OmnichannelConversationMessage {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private OmnichannelConversation conversation;

    @Column(nullable = false, length = 20)
    private String direction;

    @Column(name = "message_type", nullable = false, length = 50)
    private String messageType;

    @Column(name = "message_text", length = 4000)
    private String messageText;

    @Column(name = "raw_payload", length = 12000)
    private String rawPayload;

    @Column(name = "external_message_id", length = 255)
    private String externalMessageId;

    @Column(name = "correlation_id", length = 80)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (direction == null || direction.isBlank()) {
            direction = "INBOUND";
        }
        if (messageType == null || messageType.isBlank()) {
            messageType = "TEXT";
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
