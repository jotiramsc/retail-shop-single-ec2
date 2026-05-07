package com.retailshop.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MarketingContentUpdateRequest {
    private String captionText;
    private String hashtags;
    private String callToAction;
    private String imagePrompt;
    private String imageUrl;
    private LocalDateTime scheduledAt;
}
