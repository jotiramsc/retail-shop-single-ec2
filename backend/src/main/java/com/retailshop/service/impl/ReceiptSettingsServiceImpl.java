package com.retailshop.service.impl;

import com.retailshop.dto.ReceiptSettingsRequest;
import com.retailshop.dto.ReceiptSettingsResponse;
import com.retailshop.entity.ReceiptSettings;
import com.retailshop.repository.ReceiptSettingsRepository;
import com.retailshop.service.ReceiptSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ReceiptSettingsServiceImpl implements ReceiptSettingsService {

    private final ReceiptSettingsRepository receiptSettingsRepository;

    @Override
    @Transactional(readOnly = true)
    public ReceiptSettingsResponse getSettings() {
        return receiptSettingsRepository.findAll()
                .stream()
                .findFirst()
                .map(this::mapToResponse)
                .orElseGet(() -> ReceiptSettingsResponse.builder()
                        .showAddress(true)
                        .showPhoneNumber(true)
                        .showGstNumber(false)
                        .taxEnabled(false)
                        .cgstPercent(BigDecimal.ZERO)
                        .sgstPercent(BigDecimal.ZERO)
                        .deliveryFeeEnabled(false)
                        .deliveryFee(BigDecimal.ZERO)
                        .freeDeliveryThreshold(BigDecimal.ZERO)
                        .facebookCatalogEnabled(false)
                        .websitePrimaryColor("#2fbf91")
                        .websiteAccentColor("#c97d3a")
                        .websiteSurfaceColor("#ffffff")
                        .websiteTextColor("#2f3a4a")
                        .websiteCornerRadius("soft")
                        .websiteButtonStyle("filled")
                        .websiteDensity("comfortable")
                        .adminPrimaryColor("#2fbf91")
                        .adminAccentColor("#7367f0")
                        .adminSurfaceColor("#ffffff")
                        .adminTextColor("#2f3a4a")
                        .adminSidebarStyle("jewellery")
                        .adminHeaderCompact(true)
                        .build());
    }

    @Override
    @Transactional
    public ReceiptSettingsResponse updateSettings(ReceiptSettingsRequest request) {
        ReceiptSettings settings = receiptSettingsRepository.findAll()
                .stream()
                .findFirst()
                .orElseGet(ReceiptSettings::new);
        settings.setShopName(request.getShopName());
        settings.setHeaderLine(request.getHeaderLine());
        settings.setLogoUrl(request.getLogoUrl());
        settings.setLoginKicker(request.getLoginKicker());
        settings.setHomepageTitle(request.getHomepageTitle());
        settings.setHomepageSubtitle(request.getHomepageSubtitle());
        settings.setHeroPrimaryImageUrl(request.getHeroPrimaryImageUrl());
        settings.setHeroSecondaryImageUrl(request.getHeroSecondaryImageUrl());
        settings.setTrustBadgeOne(request.getTrustBadgeOne());
        settings.setTrustBadgeTwo(request.getTrustBadgeTwo());
        settings.setTrustBadgeThree(request.getTrustBadgeThree());
        settings.setTrustBadgeFour(request.getTrustBadgeFour());
        settings.setWebsitePrimaryColor(normalizeColor(request.getWebsitePrimaryColor(), "#2fbf91"));
        settings.setWebsiteAccentColor(normalizeColor(request.getWebsiteAccentColor(), "#c97d3a"));
        settings.setWebsiteSurfaceColor(normalizeColor(request.getWebsiteSurfaceColor(), "#ffffff"));
        settings.setWebsiteTextColor(normalizeColor(request.getWebsiteTextColor(), "#2f3a4a"));
        settings.setWebsiteCornerRadius(normalizeChoice(request.getWebsiteCornerRadius(), "soft"));
        settings.setWebsiteButtonStyle(normalizeChoice(request.getWebsiteButtonStyle(), "filled"));
        settings.setWebsiteDensity(normalizeChoice(request.getWebsiteDensity(), "comfortable"));
        settings.setAdminPrimaryColor(normalizeColor(request.getAdminPrimaryColor(), "#2fbf91"));
        settings.setAdminAccentColor(normalizeColor(request.getAdminAccentColor(), "#7367f0"));
        settings.setAdminSurfaceColor(normalizeColor(request.getAdminSurfaceColor(), "#ffffff"));
        settings.setAdminTextColor(normalizeColor(request.getAdminTextColor(), "#2f3a4a"));
        settings.setAdminSidebarStyle(normalizeChoice(request.getAdminSidebarStyle(), "jewellery"));
        settings.setAdminHeaderCompact(!Boolean.FALSE.equals(request.getAdminHeaderCompact()));
        settings.setAddress(request.getAddress());
        settings.setPhoneNumber(request.getPhoneNumber());
        settings.setGstNumber(request.getGstNumber());
        settings.setFooterNote(request.getFooterNote());
        settings.setShowAddress(request.getShowAddress());
        settings.setShowPhoneNumber(request.getShowPhoneNumber());
        settings.setShowGstNumber(request.getShowGstNumber());
        settings.setTaxEnabled(Boolean.TRUE.equals(request.getTaxEnabled()));
        settings.setCgstPercent(nonNegative(request.getCgstPercent()));
        settings.setSgstPercent(nonNegative(request.getSgstPercent()));
        settings.setDeliveryFeeEnabled(Boolean.TRUE.equals(request.getDeliveryFeeEnabled()));
        settings.setDeliveryFee(nonNegative(request.getDeliveryFee()));
        settings.setFreeDeliveryThreshold(nonNegative(request.getFreeDeliveryThreshold()));
        settings.setFacebookCatalogEnabled(Boolean.TRUE.equals(request.getFacebookCatalogEnabled()));
        settings.setMetaPixelId(normalizeOptionalText(request.getMetaPixelId()));
        settings.setFacebookFeedToken(normalizeOptionalText(request.getFacebookFeedToken()));
        return mapToResponse(receiptSettingsRepository.save(settings));
    }

    private ReceiptSettingsResponse mapToResponse(ReceiptSettings settings) {
        return ReceiptSettingsResponse.builder()
                .id(settings.getId())
                .shopName(settings.getShopName())
                .headerLine(settings.getHeaderLine())
                .logoUrl(settings.getLogoUrl())
                .loginKicker(settings.getLoginKicker())
                .homepageTitle(settings.getHomepageTitle())
                .homepageSubtitle(settings.getHomepageSubtitle())
                .heroPrimaryImageUrl(settings.getHeroPrimaryImageUrl())
                .heroSecondaryImageUrl(settings.getHeroSecondaryImageUrl())
                .trustBadgeOne(settings.getTrustBadgeOne())
                .trustBadgeTwo(settings.getTrustBadgeTwo())
                .trustBadgeThree(settings.getTrustBadgeThree())
                .trustBadgeFour(settings.getTrustBadgeFour())
                .websitePrimaryColor(normalizeColor(settings.getWebsitePrimaryColor(), "#2fbf91"))
                .websiteAccentColor(normalizeColor(settings.getWebsiteAccentColor(), "#c97d3a"))
                .websiteSurfaceColor(normalizeColor(settings.getWebsiteSurfaceColor(), "#ffffff"))
                .websiteTextColor(normalizeColor(settings.getWebsiteTextColor(), "#2f3a4a"))
                .websiteCornerRadius(normalizeChoice(settings.getWebsiteCornerRadius(), "soft"))
                .websiteButtonStyle(normalizeChoice(settings.getWebsiteButtonStyle(), "filled"))
                .websiteDensity(normalizeChoice(settings.getWebsiteDensity(), "comfortable"))
                .adminPrimaryColor(normalizeColor(settings.getAdminPrimaryColor(), "#2fbf91"))
                .adminAccentColor(normalizeColor(settings.getAdminAccentColor(), "#7367f0"))
                .adminSurfaceColor(normalizeColor(settings.getAdminSurfaceColor(), "#ffffff"))
                .adminTextColor(normalizeColor(settings.getAdminTextColor(), "#2f3a4a"))
                .adminSidebarStyle(normalizeChoice(settings.getAdminSidebarStyle(), "jewellery"))
                .adminHeaderCompact(!Boolean.FALSE.equals(settings.getAdminHeaderCompact()))
                .address(settings.getAddress())
                .phoneNumber(settings.getPhoneNumber())
                .gstNumber(settings.getGstNumber())
                .footerNote(settings.getFooterNote())
                .showAddress(settings.getShowAddress())
                .showPhoneNumber(settings.getShowPhoneNumber())
                .showGstNumber(settings.getShowGstNumber())
                .taxEnabled(Boolean.TRUE.equals(settings.getTaxEnabled()))
                .cgstPercent(nonNegative(settings.getCgstPercent()))
                .sgstPercent(nonNegative(settings.getSgstPercent()))
                .deliveryFeeEnabled(Boolean.TRUE.equals(settings.getDeliveryFeeEnabled()))
                .deliveryFee(nonNegative(settings.getDeliveryFee()))
                .freeDeliveryThreshold(nonNegative(settings.getFreeDeliveryThreshold()))
                .facebookCatalogEnabled(Boolean.TRUE.equals(settings.getFacebookCatalogEnabled()))
                .metaPixelId(settings.getMetaPixelId())
                .facebookFeedToken(settings.getFacebookFeedToken())
                .facebookFeedLastGeneratedAt(settings.getFacebookFeedLastGeneratedAt())
                .build();
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private String normalizeOptionalText(String value) {
        String normalized = value == null ? null : value.trim();
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String normalizeColor(String value, String fallback) {
        String normalized = normalizeOptionalText(value);
        return normalized != null && normalized.matches("^#[0-9a-fA-F]{6}$") ? normalized : fallback;
    }

    private String normalizeChoice(String value, String fallback) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? fallback : normalized;
    }
}
