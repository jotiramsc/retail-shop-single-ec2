package com.retailshop.dto;

import com.retailshop.enums.MarketingPlatform;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class MarketingAnalyticsResponse {
    private long impressions;
    private long likes;
    private long comments;
    private long shares;
    private long clicks;
    private long conversions;
    private long leadVisits;
    private long whatsappDelivered;
    private long whatsappFailed;
    private List<PlatformAnalyticsRow> byPlatform;
    private List<SourceAnalyticsRow> bySource;
    private List<CampaignAnalyticsRow> byCampaign;

    @Getter
    @Builder
    public static class PlatformAnalyticsRow {
        private MarketingPlatform platform;
        private long impressions;
        private long likes;
        private long comments;
        private long shares;
        private long clicks;
        private long conversions;
    }

    @Getter
    @Builder
    public static class SourceAnalyticsRow {
        private String source;
        private long visits;
    }

    @Getter
    @Builder
    public static class CampaignAnalyticsRow {
        private UUID campaignId;
        private UUID contentId;
        private String campaignName;
        private MarketingPlatform platform;
        private long impressions;
        private long likes;
        private long comments;
        private long shares;
        private long clicks;
        private long conversions;
        private LocalDateTime fetchedAt;
    }
}
