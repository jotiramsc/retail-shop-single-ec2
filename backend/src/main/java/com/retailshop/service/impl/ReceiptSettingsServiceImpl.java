package com.retailshop.service.impl;

import com.retailshop.dto.ReceiptSettingsRequest;
import com.retailshop.dto.ReceiptSettingsResponse;
import com.retailshop.entity.ReceiptSettings;
import com.retailshop.repository.ReceiptSettingsRepository;
import com.retailshop.service.ReceiptSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .build();
    }
}
