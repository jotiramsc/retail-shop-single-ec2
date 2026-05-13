package com.retailshop.controller;

import com.retailshop.config.AppProperties;
import com.retailshop.dto.WhatsAppTemplateCatalogResponse;
import com.retailshop.dto.WhatsAppTemplateSendRequest;
import com.retailshop.dto.WhatsAppTemplateSendResponse;
import com.retailshop.enums.WhatsAppTemplateKey;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.WhatsAppMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/whatsapp/templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
public class WhatsAppTemplateController {

    private final WhatsAppMessageService whatsAppMessageService;
    private final AppProperties appProperties;

    @GetMapping
    public List<WhatsAppTemplateCatalogResponse> getTemplates() {
        return Arrays.stream(WhatsAppTemplateKey.values())
                .map(key -> WhatsAppTemplateCatalogResponse.builder()
                        .key(key)
                        .templateName(resolveName(key))
                        .languageCode(resolveLanguage(key))
                        .variables(key.getVariables())
                        .build())
                .toList();
    }

    @PostMapping("/send-test")
    public WhatsAppTemplateSendResponse sendTestTemplate(@Valid @RequestBody WhatsAppTemplateSendRequest request) {
        MarketingChannelResult result = whatsAppMessageService.sendTemplate(
                request.getMobile(),
                request.getTemplateKey(),
                request.getVariables()
        );
        return WhatsAppTemplateSendResponse.builder()
                .success(result.isSuccess())
                .responseId(result.getResponseId())
                .errorMessage(result.getErrorMessage())
                .build();
    }

    private String resolveName(WhatsAppTemplateKey key) {
        if ("GUPSHUP".equalsIgnoreCase(appProperties.getWhatsappProvider())) {
            String templateId = resolveGupshupTemplateId(key);
            return templateId == null || templateId.isBlank() ? key.getDefaultTemplateName() + " (Gupshup ID missing)" : templateId;
        }
        AppProperties.Meta meta = appProperties.getMeta();
        AppProperties.WhatsappTemplates templates = meta.getWhatsappTemplates();
        AppProperties.Template template = templateFor(key, templates, meta);
        if (template == null || template.getName() == null || template.getName().isBlank()) {
            return key.getDefaultTemplateName();
        }
        return template.getName();
    }

    private String resolveLanguage(WhatsAppTemplateKey key) {
        AppProperties.Meta meta = appProperties.getMeta();
        AppProperties.WhatsappTemplates templates = meta.getWhatsappTemplates();
        AppProperties.Template template = templateFor(key, templates, meta);
        if (template == null || template.getLanguage() == null || template.getLanguage().isBlank()) {
            return key.getDefaultLanguageCode();
        }
        return template.getLanguage();
    }

    private String resolveGupshupTemplateId(WhatsAppTemplateKey key) {
        AppProperties.GupshupTemplates templates = appProperties.getGupshup() == null
                ? null
                : appProperties.getGupshup().getTemplates();
        if (templates == null) {
            return "";
        }
        return switch (key) {
            case ORDER_CONFIRMATION -> templates.getOrderConfirmation();
            case ORDER_DISPATCHED -> templates.getOrderDispatched();
            case ORDER_DELIVERED -> templates.getOrderDelivered();
            case ORDER_CANCELLED -> templates.getOrderCancelled();
            case ORDER_RETURNED -> templates.getOrderReturned();
            case REFUND_INITIATED -> templates.getRefundInitiated();
            case PAYMENT_FAILED -> templates.getPaymentFailed();
            case PAYMENT_SUCCESS -> templates.getPaymentSuccess();
            case LOGIN_OTP -> templates.getOtp();
            case BOT_WELCOME -> templates.getBotWelcome();
            case BOT_MENU -> templates.getBotMenu();
            case SUPPORT_ESCALATION -> templates.getSupportEscalation();
            case OUT_OF_OFFICE -> templates.getOutOfOffice();
            case FEEDBACK_REQUEST -> templates.getFeedbackRequest();
        };
    }

    private AppProperties.Template templateFor(WhatsAppTemplateKey key,
                                               AppProperties.WhatsappTemplates templates,
                                               AppProperties.Meta meta) {
        if (key == WhatsAppTemplateKey.LOGIN_OTP) {
            return new AppProperties.Template(meta.getWhatsappOtpTemplateName(), meta.getWhatsappOtpTemplateLanguage());
        }
        if (templates == null) {
            return null;
        }
        return switch (key) {
            case ORDER_CONFIRMATION -> templates.getOrderConfirmation();
            case ORDER_DISPATCHED -> templates.getOrderDispatched();
            case ORDER_DELIVERED -> templates.getOrderDelivered();
            case ORDER_CANCELLED -> templates.getOrderCancelled();
            case ORDER_RETURNED -> templates.getOrderReturned();
            case REFUND_INITIATED -> templates.getRefundInitiated();
            case PAYMENT_FAILED -> templates.getPaymentFailed();
            case PAYMENT_SUCCESS -> templates.getPaymentSuccess();
            case BOT_WELCOME -> templates.getBotWelcome();
            case BOT_MENU -> templates.getBotMenu();
            case SUPPORT_ESCALATION -> templates.getSupportEscalation();
            case OUT_OF_OFFICE -> templates.getOutOfOffice();
            case FEEDBACK_REQUEST -> templates.getFeedbackRequest();
            case LOGIN_OTP -> null;
        };
    }
}
