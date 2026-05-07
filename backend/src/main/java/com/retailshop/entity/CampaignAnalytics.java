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
@Table(name = "campaign_analytics")
public class CampaignAnalytics {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_content_id", nullable = false)
    private CampaignContent campaignContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketingPlatform platform;

    @Column(nullable = false)
    private long impressions;

    @Column(nullable = false)
    private long likes;

    @Column(nullable = false)
    private long comments;

    @Column(nullable = false)
    private long shares;

    @Column(nullable = false)
    private long clicks;

    @Column(nullable = false)
    private long conversions;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (fetchedAt == null) {
            fetchedAt = LocalDateTime.now();
        }
    }
}
