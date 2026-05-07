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
@Table(name = "social_webhook_events")
public class SocialWebhookEvent {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "external_event_id", length = 255)
    private String externalEventId;

    @Column(name = "signature_valid")
    private Boolean signatureValid;

    @Column(name = "raw_payload", nullable = false, length = 12000)
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}
