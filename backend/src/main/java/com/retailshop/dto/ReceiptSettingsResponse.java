package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ReceiptSettingsResponse {
    private UUID id;
    private String shopName;
    private String headerLine;
    private String logoUrl;
    private String loginKicker;
    private String homepageTitle;
    private String homepageSubtitle;
    private String heroPrimaryImageUrl;
    private String heroSecondaryImageUrl;
    private String trustBadgeOne;
    private String trustBadgeTwo;
    private String trustBadgeThree;
    private String trustBadgeFour;
    private String address;
    private String phoneNumber;
    private String gstNumber;
    private String footerNote;
    private Boolean showAddress;
    private Boolean showPhoneNumber;
    private Boolean showGstNumber;
}
