package com.retailshop.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CustomerEngagementUpdateRequest {
    private LocalDate dateOfBirth;
    private LocalDate anniversaryDate;
    private String gender;
    private String spouseName;
    private String preferredLanguage;
    private String preferredCategories;
    private String preferredProducts;
    private String preferredBrands;
    private String preferredPriceRange;
    private String shoppingInterests;
    private String customerNotes;
    private String customerTags;
    private Boolean birthdayReminderEnabled;
    private Boolean anniversaryReminderEnabled;
}
