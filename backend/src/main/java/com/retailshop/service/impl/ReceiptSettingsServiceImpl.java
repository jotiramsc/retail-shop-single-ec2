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
}
