package com.retailshop.controller;

import com.retailshop.config.OmnichannelProperties;
import com.retailshop.dto.WhatsAppBotWebhookResponse;
import com.retailshop.exception.BusinessException;
import com.retailshop.service.WhatsAppSalesBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
public class WhatsAppSalesBotController {

    private final WhatsAppSalesBotService whatsAppSalesBotService;
    private final OmnichannelProperties omnichannelProperties;

    @GetMapping(value = "/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyWebhook(@RequestParam(name = "hub.mode", required = false) String mode,
                                                @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
                                                @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!"subscribe".equalsIgnoreCase(mode)) {
            throw new BusinessException("Unsupported webhook verification mode");
        }
        if (omnichannelProperties.getWebhookVerifyToken() == null
                || omnichannelProperties.getWebhookVerifyToken().isBlank()
                || !omnichannelProperties.getWebhookVerifyToken().equals(verifyToken)) {
            throw new BusinessException("Invalid webhook verification token");
        }
        return ResponseEntity.ok(challenge == null ? "" : challenge);
    }

    @PostMapping("/webhook")
    public WhatsAppBotWebhookResponse receiveWebhook(@RequestBody(required = false) String payload,
                                                     @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
        return whatsAppSalesBotService.handleWebhook(payload == null ? "{}" : payload, signature);
    }
}
