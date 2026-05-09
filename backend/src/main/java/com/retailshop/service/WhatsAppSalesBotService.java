package com.retailshop.service;

import com.retailshop.dto.WhatsAppBotWebhookResponse;

public interface WhatsAppSalesBotService {
    WhatsAppBotWebhookResponse handleWebhook(String payload, String signature);
}
