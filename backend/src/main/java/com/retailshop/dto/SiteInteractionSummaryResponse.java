package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiteInteractionSummaryResponse {
    private long totalVisits;
    private boolean recorded;
}
