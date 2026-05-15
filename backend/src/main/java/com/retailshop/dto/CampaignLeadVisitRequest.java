package com.retailshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class CampaignLeadVisitRequest {
    @NotNull
    private UUID campaignId;

    @NotBlank
    private String source;

    private UUID productId;
    private UUID offerId;

    @NotBlank
    private String sessionId;

    private LocalDateTime timestamp;
}
