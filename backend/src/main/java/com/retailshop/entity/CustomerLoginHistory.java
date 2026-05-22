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
@Table(name = "customer_login_history")
public class CustomerLoginHistory {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "login_method", nullable = false, length = 80)
    private String loginMethod;

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "device_type", length = 120)
    private String deviceType;

    @Column(length = 160)
    private String browser;

    @Column(name = "operating_system", length = 160)
    private String operatingSystem;

    @Column(name = "source_page", length = 500)
    private String sourcePage;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(length = 500)
    private String location;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (loginAt == null) {
            loginAt = LocalDateTime.now();
        }
        if (status == null || status.isBlank()) {
            status = "SUCCESS";
        }
    }
}
