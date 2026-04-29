package com.retailshop.entity;

import com.retailshop.enums.SiteVisitSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "site_visits")
public class SiteVisit {

    @Id
    private UUID id;

    @Column(name = "visitor_id", nullable = false, length = 100)
    private String visitorId;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "landing_path", nullable = false, length = 500)
    private String landingPath;

    @Column(name = "referrer", columnDefinition = "TEXT")
    private String referrer;

    @Column(name = "referrer_host", length = 255)
    private String referrerHost;

    @Column(name = "utm_source", length = 255)
    private String utmSource;

    @Column(name = "utm_medium", length = 255)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 255)
    private String utmCampaign;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private SiteVisitSourceType sourceType;

    @Column(name = "source_label", nullable = false, length = 255)
    private String sourceLabel;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "city", length = 255)
    private String city;

    @Column(name = "region", length = 255)
    private String region;

    @Column(name = "country_name", length = 255)
    private String countryName;

    @Column(name = "country_code", length = 20)
    private String countryCode;

    @Column(name = "timezone", length = 255)
    private String timezone;

    @Column(name = "exact_location_name", length = 500)
    private String exactLocationName;

    @Column(name = "postal_code", length = 40)
    private String postalCode;

    @Column(name = "location_source", length = 40)
    private String locationSource;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "location_accuracy_meters")
    private Double locationAccuracyMeters;

    @Column(name = "organization", length = 255)
    private String organization;

    @Column(name = "accept_language", length = 255)
    private String acceptLanguage;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (visitDate == null) {
            visitDate = LocalDate.now();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
