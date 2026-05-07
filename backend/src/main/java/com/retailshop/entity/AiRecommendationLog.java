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
@Table(name = "ai_recommendation_logs")
public class AiRecommendationLog {

    @Id
    private UUID id;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(nullable = false, length = 50)
    private String channel;

    @Column(name = "search_query", length = 1000)
    private String searchQuery;

    @Column(name = "filters", length = 2000)
    private String filters;

    @Column(name = "recommended_product_ids", length = 2000)
    private String recommendedProductIds;

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
