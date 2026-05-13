package com.retailshop.dto.bot;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BotDeliveryTimeline {
    private String orderNumber;
    private String currentStage;
    private List<String> stages;
    private String etaText;
    private String supportAction;
}
