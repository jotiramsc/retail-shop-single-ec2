package com.retailshop.entity;

import com.retailshop.enums.CampaignType;
import com.retailshop.enums.MarketingCampaignType;
import com.retailshop.enums.MarketingDiscountType;
import com.retailshop.enums.MarketingLanguage;
import com.retailshop.enums.MarketingTone;
import com.retailshop.enums.MarketingWorkflowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Column(name = "campaign_name")
    private String campaignName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_type")
    private MarketingCampaignType campaignType;

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

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "offer_title")
    private String offerTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type")
    private MarketingDiscountType discountType;

    @Column(name = "discount_value", precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "target_platforms", length = 500)
    private String targetPlatforms;

    @Enumerated(EnumType.STRING)
    private MarketingLanguage language;

    @Enumerated(EnumType.STRING)
    private MarketingTone tone;

    @Enumerated(EnumType.STRING)
    private MarketingWorkflowStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (campaignName == null || campaignName.isBlank()) {
            campaignName = name;
        }
        if (name == null || name.isBlank()) {
            name = campaignName;
        }
        if (content == null) {
            content = "";
        }
        if (campaignType == null) {
            campaignType = MarketingCampaignType.CUSTOM;
        }
        if (discountType == null) {
            discountType = MarketingDiscountType.NONE;
        }
        if (language == null) {
            language = MarketingLanguage.MARATHI;
        }
        if (tone == null) {
            tone = MarketingTone.PREMIUM;
        }
        if (status == null) {
            status = draft ? MarketingWorkflowStatus.DRAFT : MarketingWorkflowStatus.PENDING_APPROVAL;
        }
        if (targetPlatforms == null || targetPlatforms.isBlank()) {
            targetPlatforms = channels;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (campaignName == null || campaignName.isBlank()) {
            campaignName = name;
        }
        if (name == null || name.isBlank()) {
            name = campaignName;
        }
        if (targetPlatforms == null || targetPlatforms.isBlank()) {
            targetPlatforms = channels;
        }
    }
}
