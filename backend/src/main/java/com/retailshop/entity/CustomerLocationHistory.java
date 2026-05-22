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
@Table(name = "customer_location_history")
public class CustomerLocationHistory {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(length = 255)
    private String city;

    @Column(length = 255)
    private String state;

    @Column(length = 255)
    private String country;

    @Column(length = 40)
    private String pincode;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Column(name = "location_source", length = 50)
    private String locationSource;

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
