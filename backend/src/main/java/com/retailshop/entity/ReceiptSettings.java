package com.retailshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "receipt_settings")
public class ReceiptSettings {

    @Id
    private UUID id;

    @Column(name = "shop_name", nullable = false)
    private String shopName;

    @Column(name = "header_line")
    private String headerLine;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "login_kicker")
    private String loginKicker;

    @Column(name = "homepage_title", length = 1000)
    private String homepageTitle;

    @Column(name = "homepage_subtitle", length = 2000)
    private String homepageSubtitle;

    @Column(name = "hero_primary_image_url", columnDefinition = "TEXT")
    private String heroPrimaryImageUrl;

    @Column(name = "hero_secondary_image_url", columnDefinition = "TEXT")
    private String heroSecondaryImageUrl;

    @Column(name = "trust_badge_one")
    private String trustBadgeOne;

    @Column(name = "trust_badge_two")
    private String trustBadgeTwo;

    @Column(name = "trust_badge_three")
    private String trustBadgeThree;

    @Column(name = "trust_badge_four")
    private String trustBadgeFour;

    @Column(name = "website_primary_color", length = 20)
    private String websitePrimaryColor;

    @Column(name = "website_accent_color", length = 20)
    private String websiteAccentColor;

    @Column(name = "website_surface_color", length = 20)
    private String websiteSurfaceColor;

    @Column(name = "website_text_color", length = 20)
    private String websiteTextColor;

    @Column(name = "website_corner_radius", length = 40)
    private String websiteCornerRadius;

    @Column(name = "website_button_style", length = 40)
    private String websiteButtonStyle;

    @Column(name = "website_density", length = 40)
    private String websiteDensity;

    @Column(name = "admin_primary_color", length = 20)
    private String adminPrimaryColor;

    @Column(name = "admin_accent_color", length = 20)
    private String adminAccentColor;

    @Column(name = "admin_surface_color", length = 20)
    private String adminSurfaceColor;

    @Column(name = "admin_text_color", length = 20)
    private String adminTextColor;

    @Column(name = "admin_sidebar_style", length = 40)
    private String adminSidebarStyle;

    @Column(name = "admin_header_compact", nullable = false)
    private Boolean adminHeaderCompact = Boolean.TRUE;

    @Column(nullable = false)
    private String address;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "gst_number")
    private String gstNumber;

    @Column(name = "footer_note")
    private String footerNote;

    @Column(name = "show_address", nullable = false)
    private Boolean showAddress;

    @Column(name = "show_phone_number", nullable = false)
    private Boolean showPhoneNumber;

    @Column(name = "show_gst_number", nullable = false)
    private Boolean showGstNumber;

    @Column(name = "tax_enabled", nullable = false)
    private Boolean taxEnabled;

    @Column(name = "cgst_percent", nullable = false, precision = 7, scale = 2)
    private BigDecimal cgstPercent;

    @Column(name = "sgst_percent", nullable = false, precision = 7, scale = 2)
    private BigDecimal sgstPercent;

    @Column(name = "delivery_fee_enabled", nullable = false)
    private Boolean deliveryFeeEnabled;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "free_delivery_threshold", nullable = false, precision = 12, scale = 2)
    private BigDecimal freeDeliveryThreshold;

    @Column(name = "facebook_catalog_enabled", nullable = false)
    private Boolean facebookCatalogEnabled;

    @Column(name = "meta_pixel_id", length = 100)
    private String metaPixelId;

    @Column(name = "facebook_feed_token")
    private String facebookFeedToken;

    @Column(name = "facebook_feed_last_generated_at")
    private LocalDateTime facebookFeedLastGeneratedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        applyDefaults();
    }

    @jakarta.persistence.PreUpdate
    public void preUpdate() {
        applyDefaults();
    }

    private void applyDefaults() {
        if (taxEnabled == null) {
            taxEnabled = Boolean.FALSE;
        }
        if (cgstPercent == null) {
            cgstPercent = BigDecimal.ZERO;
        }
        if (sgstPercent == null) {
            sgstPercent = BigDecimal.ZERO;
        }
        if (deliveryFeeEnabled == null) {
            deliveryFeeEnabled = Boolean.FALSE;
        }
        if (deliveryFee == null) {
            deliveryFee = BigDecimal.ZERO;
        }
        if (freeDeliveryThreshold == null) {
            freeDeliveryThreshold = BigDecimal.ZERO;
        }
        if (facebookCatalogEnabled == null) {
            facebookCatalogEnabled = Boolean.FALSE;
        }
        if (adminHeaderCompact == null) {
            adminHeaderCompact = Boolean.TRUE;
        }
    }
}
