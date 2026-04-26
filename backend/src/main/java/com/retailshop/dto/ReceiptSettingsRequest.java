package com.retailshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReceiptSettingsRequest {

    @NotBlank
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

    @NotBlank
    private String address;

    private String phoneNumber;

    private String gstNumber;

    private String footerNote;

    @NotNull
    private Boolean showAddress;

    @NotNull
    private Boolean showPhoneNumber;

    @NotNull
    private Boolean showGstNumber;
}
