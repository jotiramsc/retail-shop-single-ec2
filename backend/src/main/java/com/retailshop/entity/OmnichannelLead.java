package com.retailshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "omnichannel_leads")
public class OmnichannelLead {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String channel;

    @Column(name = "external_user_id", length = 255)
    private String externalUserId;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(length = 30)
    private String mobile;

    @Column(name = "source_campaign", length = 255)
    private String sourceCampaign;

    @Column(name = "product_interest", length = 500)
    private String productInterest;

    @Column(name = "latest_message", length = 2000)
    private String latestMessage;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null || status.isBlank()) {
            status = "NEW";
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
