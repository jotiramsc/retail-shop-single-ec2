package com.retailshop.entity;

import com.retailshop.enums.CampaignType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "campaigns")
public class Campaign {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignType type;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "offer_product")
    private String offerProduct;

    @Column(name = "media_url", length = 4000)
    private String mediaUrl;

    @Column(length = 1000)
    private String hashtags;

    @Column(name = "link_url", length = 2000)
    private String linkUrl;

    @Column(length = 255)
    private String channels;

    @Column(nullable = false)
    private boolean draft;

    @Column(name = "created_by")
    private String createdBy;

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
