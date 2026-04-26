package com.retailshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

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

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
