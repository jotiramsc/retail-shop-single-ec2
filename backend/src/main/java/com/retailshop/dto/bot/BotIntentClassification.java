package com.retailshop.dto.bot;

import com.retailshop.enums.WhatsAppBotIntent;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder(toBuilder = true)
public class BotIntentClassification {
    private WhatsAppBotIntent intent;
    private double confidence;
    private String category;
    private String searchText;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String occasion;
    private String orderNumber;
    private boolean needsClarification;
    private String clarificationQuestion;

    public static BotIntentClassification fallback(String message) {
        return BotIntentClassification.builder()
                .intent(WhatsAppBotIntent.FALLBACK)
                .confidence(0.1)
                .searchText(message)
                .needsClarification(true)
                .clarificationQuestion("Please choose shopping, orders, payments, offers, or support.")
                .build();
    }
}
