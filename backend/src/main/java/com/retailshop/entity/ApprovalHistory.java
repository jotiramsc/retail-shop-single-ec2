package com.retailshop.entity;

import com.retailshop.enums.MarketingApprovalAction;
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
@Table(name = "approval_history")
public class ApprovalHistory {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_content_id", nullable = false)
    private CampaignContent campaignContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketingApprovalAction action;

    @Column(length = 2000)
    private String comment;

    @Column(name = "action_by", nullable = false)
    private String actionBy;

    @Column(name = "action_at", nullable = false)
    private LocalDateTime actionAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (actionAt == null) {
            actionAt = LocalDateTime.now();
        }
    }
}
