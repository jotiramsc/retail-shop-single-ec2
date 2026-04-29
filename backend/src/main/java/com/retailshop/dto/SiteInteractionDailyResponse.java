package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class SiteInteractionDailyResponse {
    private LocalDate date;
    private long visits;
}
