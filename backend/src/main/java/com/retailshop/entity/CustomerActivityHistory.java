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
@Table(name = "customer_activity_history")
public class CustomerActivityHistory {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "activity_type", nullable = false, length = 80)
    private String activityType;

    @Column(name = "search_keyword", length = 500)
    private String searchKeyword;

    @Column(length = 255)
    private String category;

    @Column(name = "filter_used", length = 500)
    private String filterUsed;

    @Column(name = "price_range", length = 120)
    private String priceRange;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "result_count")
    private Integer resultCount;

    @Column(name = "clicked_product", length = 255)
    private String clickedProduct;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    @Column(name = "campaign_source", length = 255)
    private String campaignSource;

    @Column(length = 500)
    private String page;

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
