package com.retailshop.dto.bot;

import com.retailshop.dto.OmnichannelProductCardResponse;
import com.retailshop.enums.WhatsAppBotIntent;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(toBuilder = true)
public class BotResponse {
    private WhatsAppBotIntent intent;
    private String text;
    private List<String> quickReplies;
    private List<OmnichannelProductCardResponse> productCards;
    private List<BotOrderCard> orderCards;
    private BotDeliveryTimeline deliveryTimeline;
    private BotPaymentSummary paymentSummary;
    private boolean agentHandoff;
}
