package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SupportInboxSummaryResponse {
    private long openCount;
    private long unreadCount;
}
