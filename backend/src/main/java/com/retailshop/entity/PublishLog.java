package com.retailshop.entity;

import com.retailshop.enums.MarketingPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "publish_logs")
public class PublishLog {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_content_id", nullable = false)
    private CampaignContent campaignContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketingPlatform platform;

    @Column(name = "request_payload", length = 8000)
    private String requestPayload;

    @Column(name = "response_payload", length = 8000)
    private String responsePayload;

    @Column(nullable = false, length = 100)
    private String status;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

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
