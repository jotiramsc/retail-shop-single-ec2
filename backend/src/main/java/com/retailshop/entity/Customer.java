package com.retailshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    private UUID id;

    @Column
    private String name;

    @Column(unique = true)
    private String mobile;

    @Column(unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "auth_provider", nullable = false)
    private String authProvider = "OTP";

    @Column(name = "google_subject", unique = true)
    private String googleSubject;

    @Column(name = "mobile_verified", nullable = false)
    private boolean mobileVerified;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "profile_completed_at")
    private LocalDateTime profileCompletedAt;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "anniversary_date")
    private LocalDate anniversaryDate;

    @Column(name = "spouse_name")
    private String spouseName;

    @Column(name = "preferred_language", length = 80)
    private String preferredLanguage;

    @Column(name = "preferred_categories", length = 1000)
    private String preferredCategories;

    @Column(name = "preferred_products", length = 1000)
    private String preferredProducts;

    @Column(name = "preferred_brands", length = 1000)
    private String preferredBrands;

    @Column(name = "preferred_price_range", length = 120)
    private String preferredPriceRange;

    @Column(name = "shopping_interests", length = 1000)
    private String shoppingInterests;

    @Column(name = "customer_notes", length = 2000)
    private String customerNotes;

    @Column(name = "customer_tags", length = 1000)
    private String customerTags;

    @Column(name = "birthday_reminder_enabled", nullable = false)
    private boolean birthdayReminderEnabled = true;

    @Column(name = "anniversary_reminder_enabled", nullable = false)
    private boolean anniversaryReminderEnabled = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_method", length = 80)
    private String lastLoginMethod;

    @Column(name = "last_known_location", length = 500)
    private String lastKnownLocation;

    @Column
    private String gender;

    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;

    @Column(name = "alternate_mobile")
    private String alternateMobile;

    @Column(name = "customer_source", nullable = false)
    private String customerSource = "BOTH";

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
        if (authProvider == null || authProvider.isBlank()) {
            authProvider = "OTP";
        }
        if (customerSource == null || customerSource.isBlank()) {
            customerSource = "BOTH";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
