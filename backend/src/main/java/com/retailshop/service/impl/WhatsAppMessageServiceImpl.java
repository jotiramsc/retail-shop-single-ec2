package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.entity.Campaign;
import com.retailshop.entity.Customer;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.Product;
import com.retailshop.repository.ProductCategoryOptionRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.enums.OrderStatus;
import com.retailshop.enums.WhatsAppTemplateKey;
import com.retailshop.dto.whatsapp.WhatsAppInteractiveOption;
import com.retailshop.dto.whatsapp.WhatsAppInteractiveSection;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.OtpDeliveryService;
import com.retailshop.service.WhatsAppMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class WhatsAppMessageServiceImpl implements WhatsAppMessageService, OtpDeliveryService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    @Autowired(required = false)
    private ProductRepository productRepository;
    @Autowired(required = false)
    private ProductCategoryOptionRepository productCategoryOptionRepository;

    @Autowired
    public WhatsAppMessageServiceImpl(AppProperties appProperties, ObjectMapper objectMapper) {
        this(appProperties, objectMapper, HttpClient.newHttpClient());
    }

    WhatsAppMessageServiceImpl(AppProperties appProperties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public boolean isConfigured() {
        if (useGupshup()) {
            AppProperties.Gupshup gupshup = appProperties.getGupshup();
            return gupshup != null
                    && hasText(gupshup.getApiKey())
                    && hasText(gupshup.getSourceNumber())
                    && hasText(gupshup.getAppName());
        }
        AppProperties.Meta meta = appProperties.getMeta();
        return meta != null
                && hasText(meta.getAccessToken())
                && hasText(meta.getWhatsappPhoneNumberId());
    }

    @Override
    public String getChannel() {
        return "WHATSAPP";
    }

    @Override
    public MarketingChannelResult sendOtp(String mobile, String otp, long otpTtlMinutes) {
        if (useGupshup()) {
            AppProperties.Gupshup gupshup = appProperties.getGupshup();
            String templateId = gupshup == null || gupshup.getTemplates() == null ? "" : gupshup.getTemplates().getOtp();
            if (!hasText(templateId)) {
                return MarketingChannelResult.builder()
                        .success(false)
                        .errorMessage("Gupshup OTP template id is not configured")
                        .build();
            }
            MarketingChannelResult result = sendGupshupTemplateMessage(
                    mobile,
                    templateId,
                    List.of(safeText(otp), "Login", safeText(otp), "Login")
            );
            if (!result.isSuccess()) {
                log.warn("Gupshup WhatsApp OTP template send failed for {}: {}", mobile, result.getErrorMessage());
            }
            return result;
        }

        AppProperties.Meta meta = appProperties.getMeta();
        if (!hasText(meta.getWhatsappOtpTemplateName())) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("WhatsApp OTP template is not configured for the selected sender phone")
                    .build();
        }

        MarketingChannelResult templateResult = sendOtpTemplateMessage(
                mobile,
                meta.getWhatsappOtpTemplateName(),
                defaultString(meta.getWhatsappOtpTemplateLanguage(), "en_US"),
                otp
        );
        if (!templateResult.isSuccess()) {
            log.warn("WhatsApp OTP template send failed for {}: {}", mobile, templateResult.getErrorMessage());
        }
        return templateResult;
    }

    @Override
    public MarketingChannelResult sendTemplate(String mobile, WhatsAppTemplateKey templateKey, List<String> variables) {
        if (useGupshup()) {
            String templateId = resolveGupshupTemplateId(templateKey);
            if (hasText(templateId)) {
                MarketingChannelResult templateResult = sendGupshupTemplateMessage(mobile, templateId, variables);
                if (templateResult.isSuccess()) {
                    return templateResult;
                }
                log.warn("Gupshup WhatsApp template {} failed, falling back to text: {}", templateKey, templateResult.getErrorMessage());
            }
            return sendSingleTextMessage(mobile, fallbackText(templateKey, variables));
        }

        TemplateSpec template = resolveTemplate(templateKey);
        if (hasText(template.name())) {
            MarketingChannelResult templateResult = sendTemplateMessage(mobile, template.name(), template.language(), variables);
            if (templateResult.isSuccess() || !canFallbackToText(templateResult.getErrorMessage())) {
                return templateResult;
            }
            log.warn("WhatsApp template {} failed, falling back to text for test sender: {}", templateKey, templateResult.getErrorMessage());
        }
        return sendSingleTextMessage(mobile, fallbackText(templateKey, variables));
    }

    @Override
    public MarketingChannelResult sendText(String mobile, String body) {
        return sendSingleTextMessage(mobile, safeText(body));
    }

    @Override
    public MarketingChannelResult sendImage(String mobile, String imageUrl, String caption) {
        String safeImageUrl = safeText(imageUrl);
        if (!hasText(safeImageUrl)) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("WhatsApp image URL is required")
                    .build();
        }
        if (useGupshup()) {
            return sendGupshupImageMessage(mobile, safeImageUrl, safeText(caption));
        }
        if (!isConfigured()) {
            log.info("WhatsApp image fallback log for {}: {}", mobile, safeImageUrl);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage(whatsAppConfigurationError())
                    .build();
        }
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("link", safeImageUrl);
        if (hasText(caption)) {
            image.put("caption", safeText(caption));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", formatWhatsAppRecipient(mobile));
        payload.put("type", "image");
        payload.put("image", image);
        return sendMetaMessage(payload);
    }

    @Override
    public MarketingChannelResult sendReplyButtons(String mobile, String body, List<WhatsAppInteractiveOption> buttons) {
        List<WhatsAppInteractiveOption> safeButtons = buttons == null ? List.of() : buttons.stream()
                .filter(option -> option != null && hasText(option.title()))
                .limit(3)
                .toList();
        if (safeButtons.isEmpty()) {
            return sendText(mobile, body);
        }
        if (!useGupshup()) {
            return sendText(mobile, body + "\n\n" + formatOptionsForFallback(safeButtons));
        }
        MarketingChannelResult result = sendGupshupQuickReplyMessage(mobile, body, safeButtons);
        if (result.isSuccess()) {
            return result;
        }
        log.warn("Gupshup quick reply failed, falling back to text: {}", result.getErrorMessage());
        return sendText(mobile, body + "\n\n" + formatOptionsForFallback(safeButtons));
    }

    @Override
    public MarketingChannelResult sendListMessage(String mobile,
                                                  String header,
                                                  String body,
                                                  String buttonText,
                                                  List<WhatsAppInteractiveSection> sections) {
        List<WhatsAppInteractiveSection> safeSections = normalizeSections(sections);
        if (safeSections.isEmpty()) {
            return sendText(mobile, body);
        }
        if (!useGupshup()) {
            return sendText(mobile, body + "\n\n" + formatSectionsForFallback(safeSections));
        }
        MarketingChannelResult result = sendGupshupListMessage(mobile, header, body, buttonText, safeSections);
        if (result.isSuccess()) {
            return result;
        }
        log.warn("Gupshup list message failed, falling back to text: {}", result.getErrorMessage());
        return sendText(mobile, body + "\n\n" + formatSectionsForFallback(safeSections));
    }

    @Override
    public MarketingChannelResult sendOrderConfirmation(CustomerOrder order) {
        return sendTemplate(orderMobile(order), WhatsAppTemplateKey.ORDER_CONFIRMATION, List.of(
                customerName(order),
                orderNumber(order),
                formatCurrency(order.getFinalAmount()),
                estimatedDeliveryDate()
        ));
    }

    @Override
    public MarketingChannelResult sendOrderDispatched(CustomerOrder order, String trackingId, String trackingUrl) {
        return sendTemplate(orderMobile(order), WhatsAppTemplateKey.ORDER_DISPATCHED, List.of(
                customerName(order),
                orderNumber(order),
                defaultString(trackingId, "Will be shared soon"),
                defaultString(trackingUrl, appWebsiteUrl())
        ));
    }

    @Override
    public MarketingChannelResult sendOrderDelivered(CustomerOrder order) {
        return sendTemplate(orderMobile(order), WhatsAppTemplateKey.ORDER_DELIVERED, List.of(customerName(order), orderNumber(order)));
    }

    @Override
    public MarketingChannelResult sendOrderCancelled(CustomerOrder order) {
        return sendTemplate(orderMobile(order), WhatsAppTemplateKey.ORDER_CANCELLED, List.of(customerName(order), orderNumber(order)));
    }

    @Override
    public MarketingChannelResult sendOrderReturned(CustomerOrder order) {
        return sendTemplate(orderMobile(order), WhatsAppTemplateKey.ORDER_RETURNED, List.of(customerName(order), orderNumber(order)));
    }

    @Override
    public MarketingChannelResult sendRefundInitiated(CustomerOrder order, String refundAmount) {
        return sendTemplate(orderMobile(order), WhatsAppTemplateKey.REFUND_INITIATED, List.of(
                customerName(order),
                orderNumber(order),
                defaultString(refundAmount, formatCurrency(order.getFinalAmount()))
        ));
    }

    @Override
    public MarketingChannelResult sendPaymentSuccess(CustomerOrder order) {
        return sendTemplate(orderMobile(order), WhatsAppTemplateKey.PAYMENT_SUCCESS, List.of(
                customerName(order),
                orderNumber(order),
                formatCurrency(order.getFinalAmount())
        ));
    }

    @Override
    public MarketingChannelResult sendPaymentFailed(CustomerOrder order) {
        return sendTemplate(orderMobile(order), WhatsAppTemplateKey.PAYMENT_FAILED, List.of(
                customerName(order),
                orderNumber(order),
                formatCurrency(order.getFinalAmount())
        ));
    }

    @Override
    public MarketingChannelResult sendBotWelcome(String mobile, String customerName) {
        return sendTemplate(mobile, WhatsAppTemplateKey.BOT_WELCOME, List.of(defaultString(customerName, "Customer")));
    }

    @Override
    public MarketingChannelResult sendBotMenu(String mobile, String customerName) {
        return sendTemplate(mobile, WhatsAppTemplateKey.BOT_MENU, List.of(defaultString(customerName, "Customer")));
    }

    @Override
    public MarketingChannelResult sendSupportEscalation(String mobile, String customerName) {
        return sendTemplate(mobile, WhatsAppTemplateKey.SUPPORT_ESCALATION, List.of(defaultString(customerName, "Customer")));
    }

    @Override
    public MarketingChannelResult sendOutOfOffice(String mobile, String customerName) {
        return sendTemplate(mobile, WhatsAppTemplateKey.OUT_OF_OFFICE, List.of(defaultString(customerName, "Customer")));
    }

    @Override
    public MarketingChannelResult sendFeedbackRequest(CustomerOrder order) {
        return sendTemplate(orderMobile(order), WhatsAppTemplateKey.FEEDBACK_REQUEST, List.of(customerName(order), orderNumber(order)));
    }

    @Override
    public MarketingChannelResult sendOrderUpdate(CustomerOrder order) {
        if (order == null || order.getStatus() == null) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Order status is required for WhatsApp update")
                    .build();
        }
        if (order.getStatus() == OrderStatus.SHIPPED) {
            return sendOrderDispatched(order, null, null);
        }
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.COMPLETED) {
            return sendOrderDelivered(order);
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return sendOrderCancelled(order);
        }
        if (order.getStatus() == OrderStatus.RETURNED) {
            return sendOrderReturned(order);
        }
        if (order.getStatus() == OrderStatus.REFUND_INITIATED) {
            return sendRefundInitiated(order, null);
        }
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            return sendPaymentFailed(order);
        }
        return sendOrderConfirmation(order);
    }

    @Override
    public MarketingChannelResult broadcastOffer(List<Customer> customers, String content) {
        return broadcast(customers, safeText(content));
    }

    @Override
    public MarketingChannelResult publishCampaign(Campaign campaign, List<Customer> customers) {
        return broadcastCampaign(customers, buildCampaignBody(campaign), campaignImageUrl(campaign));
    }

    private MarketingChannelResult broadcast(List<Customer> customers, String body) {
        return broadcastCampaign(customers, body, null);
    }

    private String campaignImageUrl(Campaign campaign) {
        if (campaign == null) {
            return null;
        }
        if (hasText(campaign.getMediaUrl())) {
            return campaign.getMediaUrl();
        }
        if (campaign.getProductId() != null && productRepository != null) {
            return productRepository.findById(campaign.getProductId())
                    .map(Product::getImageDataUrl)
                    .filter(this::hasText)
                    .orElse(null);
        }
        if (campaign.getCategoryId() != null && productCategoryOptionRepository != null) {
            return productCategoryOptionRepository.findById(campaign.getCategoryId())
                    .map(category -> category.getIconImageUrl())
                    .filter(this::hasText)
                    .orElse(null);
        }
        return null;
    }

    private MarketingChannelResult broadcastCampaign(List<Customer> customers, String body, String imageUrl) {
        if (!isConfigured()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage(whatsAppConfigurationError())
                    .build();
        }
        List<String> recipients = normalizeBroadcastRecipients(customers);
        if (recipients.isEmpty()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("No WhatsApp recipients available")
                    .totalRecipients(0)
                    .sentCount(0)
                    .failedCount(0)
                    .build();
        }

        int sentCount = 0;
        int failedCount = 0;
        String firstError = null;
        List<String> deliveryRows = new ArrayList<>();
        String publicImageUrl = publicImageUrl(imageUrl);
        boolean hasImage = hasText(publicImageUrl);
        for (String mobile : recipients) {
            MarketingChannelResult result = hasImage
                    ? sendImage(mobile, publicImageUrl, body)
                    : sendSingleTextMessage(mobile, body);
            if (result.isSuccess()) {
                sentCount++;
                deliveryRows.add(mobile + "=SENT" + (hasText(result.getResponseId()) ? "(" + result.getResponseId() + ")" : ""));
            } else {
                failedCount++;
                String error = defaultString(result.getErrorMessage(), "Unknown WhatsApp send failure");
                deliveryRows.add(mobile + "=FAILED(" + error + ")");
                if (!hasText(firstError)) {
                    firstError = error;
                }
            }
        }

        String summary = "sent=" + sentCount + "/" + recipients.size() + ";failed=" + failedCount;
        String report = summary + ";details=" + String.join(",", deliveryRows);
        return MarketingChannelResult.builder()
                .success(failedCount == 0)
                .responseId("broadcast:" + summary)
                .errorMessage(failedCount == 0 ? null : firstError)
                .totalRecipients(recipients.size())
                .sentCount(sentCount)
                .failedCount(failedCount)
                .deliveryReport(report.length() > 8000 ? report.substring(0, 7997) + "..." : report)
                .build();
    }

    private MarketingChannelResult sendSingleTextMessage(String mobile, String body) {
        if (useGupshup()) {
            return sendGupshupTextMessage(mobile, body);
        }
        if (!isConfigured()) {
            log.info("WhatsApp fallback log for {}: {}", mobile, body);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage(whatsAppConfigurationError())
                    .build();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", formatWhatsAppRecipient(mobile));
        payload.put("type", "text");
        payload.put("text", Map.of(
                "preview_url", true,
                "body", body
        ));
        return sendMetaMessage(payload);
    }

    private MarketingChannelResult sendTemplateMessage(String mobile, String templateName, String languageCode, List<String> bodyParams) {
        if (!isConfigured()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage(whatsAppConfigurationError())
                    .build();
        }
        List<Map<String, String>> parameters = bodyParams == null ? List.of() : bodyParams.stream()
                .map(value -> Map.of("type", "text", "text", safeText(value)))
                .toList();

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName.trim());
        template.put("language", Map.of("code", defaultString(languageCode, "en_US")));
        if (!parameters.isEmpty()) {
            template.put("components", List.of(Map.of(
                    "type", "body",
                    "parameters", parameters
            )));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", formatWhatsAppRecipient(mobile));
        payload.put("type", "template");
        payload.put("template", template);
        return sendMetaMessage(payload);
    }

    private MarketingChannelResult sendOtpTemplateMessage(String mobile, String templateName, String languageCode, String otp) {
        if (!isConfigured()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage(whatsAppConfigurationError())
                    .build();
        }
        String code = safeText(otp);
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName.trim());
        template.put("language", Map.of("code", defaultString(languageCode, "en_US")));
        template.put("components", List.of(
                Map.of(
                        "type", "body",
                        "parameters", List.of(Map.of("type", "text", "text", code))
                ),
                Map.of(
                        "type", "button",
                        "sub_type", "url",
                        "index", "0",
                        "parameters", List.of(Map.of("type", "text", "text", code))
                )
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", formatWhatsAppRecipient(mobile));
        payload.put("type", "template");
        payload.put("template", template);
        return sendMetaMessage(payload);
    }

    private MarketingChannelResult sendGupshupTextMessage(String mobile, String body) {
        if (!isConfigured()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage(whatsAppConfigurationError())
                    .build();
        }
        try {
            Map<String, String> formData = baseGupshupFormData(mobile);
            formData.put("message", objectMapper.writeValueAsString(Map.of(
                    "type", "text",
                    "text", safeText(body)
            )));
            return sendGupshupForm(appProperties.getGupshup().getMessageEndpoint(), formData);
        } catch (IOException exception) {
            log.warn("Unable to build Gupshup WhatsApp text payload", exception);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Unable to send WhatsApp message with Gupshup")
                    .build();
        }
    }

    private MarketingChannelResult sendGupshupImageMessage(String mobile, String imageUrl, String caption) {
        if (!isConfigured()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage(whatsAppConfigurationError())
                    .build();
        }
        try {
            Map<String, String> formData = baseGupshupFormData(mobile);
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "image");
            message.put("originalUrl", imageUrl);
            message.put("previewUrl", imageUrl);
            if (hasText(caption)) {
                message.put("caption", safeText(caption));
            }
            formData.put("message", objectMapper.writeValueAsString(message));
            return sendGupshupForm(appProperties.getGupshup().getMessageEndpoint(), formData);
        } catch (IOException exception) {
            log.warn("Unable to build Gupshup WhatsApp image payload", exception);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Unable to send WhatsApp image with Gupshup")
                    .build();
        }
    }

    private MarketingChannelResult sendGupshupQuickReplyMessage(String mobile, String body, List<WhatsAppInteractiveOption> buttons) {
        if (!isConfigured()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage(whatsAppConfigurationError())
                    .build();
        }
        try {
            Map<String, String> formData = baseGupshupFormData(mobile);
            List<Map<String, Object>> options = new ArrayList<>();
            for (WhatsAppInteractiveOption button : buttons) {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("type", "text");
                option.put("title", truncate(button.title(), 20));
                option.put("postbackText", firstText(button.id(), button.title()));
                options.add(option);
            }
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("type", "text");
            content.put("text", safeText(body));

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "quick_reply");
            message.put("msgid", UUID.randomUUID().toString());
            message.put("content", content);
            message.put("options", options);
            formData.put("message", objectMapper.writeValueAsString(message));
            return sendGupshupForm(appProperties.getGupshup().getMessageEndpoint(), formData);
        } catch (IOException exception) {
            log.warn("Unable to build Gupshup WhatsApp quick reply payload", exception);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Unable to send WhatsApp quick reply with Gupshup")
                    .build();
        }
    }

    private MarketingChannelResult sendGupshupListMessage(String mobile,
                                                          String header,
                                                          String body,
                                                          String buttonText,
                                                          List<WhatsAppInteractiveSection> sections) {
        if (!isConfigured()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage(whatsAppConfigurationError())
                    .build();
        }
        try {
            Map<String, String> formData = baseGupshupFormData(mobile);
            List<Map<String, Object>> items = new ArrayList<>();
            for (WhatsAppInteractiveSection section : sections) {
                List<Map<String, Object>> options = new ArrayList<>();
                for (WhatsAppInteractiveOption row : section.options()) {
                    Map<String, Object> option = new LinkedHashMap<>();
                    option.put("type", "text");
                    option.put("title", truncate(row.title(), 24));
                    if (hasText(row.description())) {
                        option.put("description", truncate(row.description(), 72));
                    }
                    option.put("postbackText", firstText(row.title(), row.id()));
                    options.add(option);
                }
                if (!options.isEmpty()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("title", truncate(defaultString(section.title(), "Options"), 24));
                    item.put("subtitle", "");
                    item.put("options", options);
                    items.add(item);
                }
            }

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "list");
            message.put("title", truncate(defaultString(header, "Krishnai Pearl Shopee"), 60));
            message.put("body", safeText(body));
            message.put("msgid", UUID.randomUUID().toString());
            message.put("globalButtons", List.of(Map.of("type", "text", "title", truncate(defaultString(buttonText, "Choose"), 20))));
            message.put("items", items);
            formData.put("message", objectMapper.writeValueAsString(message));
            return sendGupshupForm(appProperties.getGupshup().getMessageEndpoint(), formData);
        } catch (IOException exception) {
            log.warn("Unable to build Gupshup WhatsApp list payload", exception);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Unable to send WhatsApp list with Gupshup")
                    .build();
        }
    }

    private MarketingChannelResult sendGupshupTemplateMessage(String mobile, String templateId, List<String> params) {
        if (!isConfigured()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage(whatsAppConfigurationError())
                    .build();
        }
        if (!hasText(templateId)) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Gupshup template id is required")
                    .build();
        }
        try {
            Map<String, String> formData = baseGupshupFormData(mobile);
            List<String> safeParams = params == null ? List.of() : params.stream()
                    .map(this::safeText)
                    .toList();
            formData.put("template", objectMapper.writeValueAsString(Map.of(
                    "id", templateId.trim(),
                    "params", safeParams
            )));
            return sendGupshupForm(appProperties.getGupshup().getTemplateEndpoint(), formData);
        } catch (IOException exception) {
            log.warn("Unable to build Gupshup WhatsApp template payload", exception);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Unable to send WhatsApp template with Gupshup")
                    .build();
        }
    }

    private Map<String, String> baseGupshupFormData(String mobile) {
        AppProperties.Gupshup gupshup = appProperties.getGupshup();
        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("channel", "whatsapp");
        formData.put("source", digitsOnly(gupshup.getSourceNumber()));
        formData.put("destination", formatWhatsAppRecipient(mobile));
        formData.put("src.name", gupshup.getAppName().trim());
        return formData;
    }

    private MarketingChannelResult sendGupshupForm(String endpoint, Map<String, String> formData) {
        AppProperties.Gupshup gupshup = appProperties.getGupshup();
        int attempts = Math.max(1, gupshup.getMaxAttempts());
        long retryDelayMs = Math.max(0L, gupshup.getRetryDelayMs());
        MarketingChannelResult lastResult = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            lastResult = sendGupshupFormOnce(endpoint, formData);
            if (lastResult.isSuccess() || attempt == attempts || !shouldRetry(lastResult.getErrorMessage())) {
                return lastResult;
            }
            sleepBeforeRetry(retryDelayMs);
        }
        return lastResult == null ? MarketingChannelResult.builder()
                .success(false)
                .errorMessage("Unable to send WhatsApp message with Gupshup")
                .build() : lastResult;
    }

    private MarketingChannelResult sendGupshupFormOnce(String endpoint, Map<String, String> formData) {
        try {
            AppProperties.Gupshup gupshup = appProperties.getGupshup();
            HttpRequest request = HttpRequest.newBuilder(URI.create(defaultString(endpoint, "https://api.gupshup.io/wa/api/v1/msg")))
                    .POST(HttpRequest.BodyPublishers.ofString(toFormBody(formData)))
                    .header("apikey", gupshup.getApiKey().trim())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Cache-Control", "no-cache")
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = parseJson(response.body());
            if (response.statusCode() >= 400 || isGupshupError(body)) {
                return MarketingChannelResult.builder()
                        .success(false)
                        .errorMessage(extractGupshupError(body, "Unable to send WhatsApp message with Gupshup"))
                        .build();
            }
            return MarketingChannelResult.builder()
                    .success(true)
                    .responseId(firstText(
                            body.path("messageId").asText(""),
                            body.path("message").path("messageId").asText(""),
                            body.path("id").asText(""),
                            body.path("message").path("id").asText(""),
                            body.path("messages").path(0).path("id").asText("")
                    ))
                    .build();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Unable to send WhatsApp message through Gupshup", exception);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Unable to send WhatsApp message with Gupshup")
                    .build();
        }
    }

    private MarketingChannelResult sendMetaMessage(Map<String, Object> payload) {
        AppProperties.Meta meta = appProperties.getMeta();
        int attempts = Math.max(1, meta.getWhatsappMaxAttempts());
        long retryDelayMs = Math.max(0L, meta.getWhatsappRetryDelayMs());
        MarketingChannelResult lastResult = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            lastResult = sendMetaMessageOnce(payload);
            if (lastResult.isSuccess() || attempt == attempts || !shouldRetry(lastResult.getErrorMessage())) {
                return lastResult;
            }
            sleepBeforeRetry(retryDelayMs);
        }
        return lastResult == null ? MarketingChannelResult.builder()
                .success(false)
                .errorMessage("Unable to send WhatsApp message")
                .build() : lastResult;
    }

    private MarketingChannelResult sendMetaMessageOnce(Map<String, Object> payload) {
        try {
            AppProperties.Meta meta = appProperties.getMeta();
            String version = defaultString(meta.getGraphVersion(), "v23.0");
            String endpoint = "https://graph.facebook.com/" + version + "/" + meta.getWhatsappPhoneNumberId().trim() + "/messages";
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .header("Authorization", "Bearer " + meta.getAccessToken().trim())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = parseJson(response.body());
            if (response.statusCode() >= 400) {
                return MarketingChannelResult.builder()
                        .success(false)
                        .errorMessage(extractError(body, "Unable to send WhatsApp message"))
                        .build();
            }
            return MarketingChannelResult.builder()
                    .success(true)
                    .responseId(body.path("messages").path(0).path("id").asText(""))
                    .build();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Unable to send WhatsApp message through Meta", exception);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Unable to send WhatsApp message")
                    .build();
        }
    }

    private TemplateSpec resolveTemplate(WhatsAppTemplateKey key) {
        AppProperties.Meta meta = appProperties.getMeta();
        AppProperties.WhatsappTemplates templates = meta.getWhatsappTemplates();
        String defaultLanguage = templates == null ? "en_US" : defaultString(templates.getDefaultLanguage(), "en_US");
        AppProperties.Template template = switch (key) {
            case ORDER_CONFIRMATION -> templates == null ? null : templates.getOrderConfirmation();
            case ORDER_DISPATCHED -> templates == null ? null : templates.getOrderDispatched();
            case ORDER_DELIVERED -> templates == null ? null : templates.getOrderDelivered();
            case ORDER_CANCELLED -> templates == null ? null : templates.getOrderCancelled();
            case ORDER_RETURNED -> templates == null ? null : templates.getOrderReturned();
            case REFUND_INITIATED -> templates == null ? null : templates.getRefundInitiated();
            case PAYMENT_FAILED -> templates == null ? null : templates.getPaymentFailed();
            case PAYMENT_SUCCESS -> templates == null ? null : templates.getPaymentSuccess();
            case BOT_WELCOME -> templates == null ? null : templates.getBotWelcome();
            case BOT_MENU -> templates == null ? null : templates.getBotMenu();
            case SUPPORT_ESCALATION -> templates == null ? null : templates.getSupportEscalation();
            case OUT_OF_OFFICE -> templates == null ? null : templates.getOutOfOffice();
            case FEEDBACK_REQUEST -> templates == null ? null : templates.getFeedbackRequest();
            case LOGIN_OTP -> new AppProperties.Template(meta.getWhatsappOtpTemplateName(), meta.getWhatsappOtpTemplateLanguage());
        };
        return new TemplateSpec(
                template == null ? key.getDefaultTemplateName() : defaultString(template.getName(), key.getDefaultTemplateName()),
                template == null ? defaultLanguage : defaultString(template.getLanguage(), defaultLanguage)
        );
    }

    private String resolveGupshupTemplateId(WhatsAppTemplateKey key) {
        AppProperties.Gupshup gupshup = appProperties.getGupshup();
        AppProperties.GupshupTemplates templates = gupshup == null ? null : gupshup.getTemplates();
        if (templates == null) {
            return "";
        }
        return switch (key) {
            case LOGIN_OTP -> templates.getOtp();
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
        };
    }

    private String fallbackText(WhatsAppTemplateKey key, List<String> variables) {
        List<String> values = variables == null ? List.of() : variables;
        String first = valueAt(values, 0);
        String second = valueAt(values, 1);
        String third = valueAt(values, 2);
        String fourth = valueAt(values, 3);
        return switch (key) {
            case ORDER_CONFIRMATION -> "Hi " + first + ", your order " + second + " has been successfully placed.\nTotal Amount: " + third + "\nEstimated Delivery: " + fourth;
            case ORDER_DISPATCHED -> "Hi " + first + ", your order " + second + " has been dispatched.\nTracking ID: " + third + "\nTrack here: " + fourth;
            case ORDER_DELIVERED -> "Hi " + first + ", your order " + second + " has been delivered successfully. Thank you for shopping with us.";
            case ORDER_CANCELLED -> "Hi " + first + ", your order " + second + " has been cancelled. Please contact support if you need help.";
            case ORDER_RETURNED -> "Hi " + first + ", your return request for order " + second + " has been received. We will update you shortly.";
            case REFUND_INITIATED -> "Hi " + first + ", refund for order " + second + " has been initiated. Amount: " + third + ".";
            case PAYMENT_FAILED -> "Hi " + first + ", payment for order " + second + " was not completed. Amount: " + third + ". Please try again from the website.";
            case PAYMENT_SUCCESS -> "Hi " + first + ", payment for order " + second + " was successful. Amount: " + third + ".";
            case LOGIN_OTP -> "Your OTP for login is " + first + ". Valid for 5 minutes. Do not share it with anyone.";
            case BOT_WELCOME -> "Hi " + first + ", welcome to our service. How can we help you today?";
            case BOT_MENU -> "Hi " + first + ", choose an option:\n1. Track Order\n2. Talk to Support\n3. Return Order\n4. FAQs";
            case SUPPORT_ESCALATION -> "Hi " + first + ", our support team has been notified and will contact you soon.";
            case OUT_OF_OFFICE -> "Hi " + first + ", our team is currently away. Please leave your query and we will respond as soon as possible.";
            case FEEDBACK_REQUEST -> "Hi " + first + ", your order " + second + " was delivered. Please share your feedback with us.";
        };
    }

    private boolean canFallbackToText(String errorMessage) {
        String normalized = safeText(errorMessage).toLowerCase();
        return normalized.contains("template")
                || normalized.contains("translation")
                || normalized.contains("does not exist")
                || normalized.contains("parameter")
                || normalized.contains("permission")
                || normalized.contains("unsupported");
    }

    private boolean shouldRetry(String errorMessage) {
        String normalized = safeText(errorMessage).toLowerCase();
        return normalized.contains("temporarily")
                || normalized.contains("timeout")
                || normalized.contains("rate")
                || normalized.contains("too many")
                || normalized.contains("try again")
                || normalized.contains("unable to send");
    }

    private void sleepBeforeRetry(long retryDelayMs) {
        if (retryDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildCampaignBody(Campaign campaign) {
        StringBuilder builder = new StringBuilder();
        if (campaign.getName() != null && !campaign.getName().isBlank()) {
            builder.append(campaign.getName().trim());
        }
        if (campaign.getOfferProduct() != null && !campaign.getOfferProduct().isBlank()) {
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append(campaign.getOfferProduct().trim());
        }
        if (campaign.getContent() != null && !campaign.getContent().isBlank()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(campaign.getContent().trim());
        }
        if (campaign.getLinkUrl() != null && !campaign.getLinkUrl().isBlank()) {
            builder.append("\n").append(campaign.getLinkUrl().trim());
        }
        return builder.toString().trim();
    }

    private JsonNode parseJson(String payload) throws IOException {
        if (payload == null || payload.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(payload);
    }

    private String extractError(JsonNode payload, String fallback) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return fallback;
        }
        String nested = payload.path("error").path("message").asText("");
        if (!nested.isBlank()) {
            return nested;
        }
        String direct = payload.path("message").asText("");
        return direct.isBlank() ? fallback : direct;
    }

    private boolean useGupshup() {
        return "GUPSHUP".equalsIgnoreCase(safeText(appProperties.getWhatsappProvider()));
    }

    private boolean isGupshupError(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return false;
        }
        String status = payload.path("status").asText("");
        if ("error".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
            return true;
        }
        return !payload.path("error").isMissingNode() && !payload.path("error").isNull();
    }

    private String extractGupshupError(JsonNode payload, String fallback) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return fallback;
        }
        String nestedMessage = payload.path("message").path("message").asText("");
        if (hasText(nestedMessage)) {
            return nestedMessage;
        }
        String nestedError = payload.path("error").path("message").asText("");
        if (hasText(nestedError)) {
            return nestedError;
        }
        String directMessage = payload.path("message").asText("");
        if (hasText(directMessage)) {
            return directMessage;
        }
        String directError = payload.path("error").asText("");
        if (hasText(directError)) {
            return directError;
        }
        return fallback;
    }

    private String toFormBody(Map<String, String> formData) {
        return formData.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private List<WhatsAppInteractiveSection> normalizeSections(List<WhatsAppInteractiveSection> sections) {
        if (sections == null) {
            return List.of();
        }
        List<WhatsAppInteractiveSection> safeSections = new ArrayList<>();
        for (WhatsAppInteractiveSection section : sections) {
            if (section == null || section.options() == null) {
                continue;
            }
            List<WhatsAppInteractiveOption> options = section.options().stream()
                    .filter(option -> option != null && hasText(option.title()))
                    .limit(10)
                    .toList();
            if (!options.isEmpty()) {
                safeSections.add(new WhatsAppInteractiveSection(defaultString(section.title(), "Options"), options));
            }
        }
        return safeSections.stream().limit(10).toList();
    }

    private String formatOptionsForFallback(List<WhatsAppInteractiveOption> options) {
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (WhatsAppInteractiveOption option : options) {
            lines.add(index++ + ". " + option.title());
        }
        return String.join("\n", lines);
    }

    private String formatSectionsForFallback(List<WhatsAppInteractiveSection> sections) {
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (WhatsAppInteractiveSection section : sections) {
            if (hasText(section.title())) {
                lines.add(section.title());
            }
            for (WhatsAppInteractiveOption option : section.options()) {
                lines.add(index++ + ". " + option.title()
                        + (hasText(option.description()) ? " - " + option.description() : ""));
            }
        }
        return String.join("\n", lines);
    }

    private String truncate(String value, int maxLength) {
        String safe = safeText(value);
        if (maxLength <= 0 || safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private String whatsAppConfigurationError() {
        if (useGupshup()) {
            return "Gupshup WhatsApp sender needs API key, source number, and app name";
        }
        return "Meta WhatsApp sender needs access token and phone number id";
    }

    private String formatWhatsAppRecipient(String mobile) {
        String digits = mobile == null ? "" : mobile.replaceAll("\\D", "");
        if (digits.length() == 10) {
            return "91" + digits;
        }
        return digits;
    }

    private List<String> normalizeBroadcastRecipients(List<Customer> customers) {
        if (customers == null || customers.isEmpty()) {
            return List.of();
        }
        Set<String> recipients = new LinkedHashSet<>();
        for (Customer customer : customers) {
            String mobile = customer == null ? null : customer.getMobile();
            String formatted = formatWhatsAppRecipient(mobile);
            if (hasText(formatted)) {
                recipients.add(formatted);
            }
        }
        return new ArrayList<>(recipients);
    }

    private String publicImageUrl(String imageUrl) {
        if (!hasText(imageUrl)) {
            return null;
        }
        String trimmed = imageUrl.trim();
        if (trimmed.startsWith("data:")) {
            return null;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("/")) {
            return appWebsiteUrl() + trimmed;
        }
        if (trimmed.startsWith("api/")) {
            return appWebsiteUrl() + "/" + trimmed;
        }
        return trimmed;
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String customerName(CustomerOrder order) {
        Customer customer = order == null ? null : order.getCustomer();
        return customer == null ? "Customer" : defaultString(customer.getName(), "Customer");
    }

    private String orderMobile(CustomerOrder order) {
        Customer customer = order == null ? null : order.getCustomer();
        return customer == null ? "" : safeText(customer.getMobile());
    }

    private String orderNumber(CustomerOrder order) {
        return order == null ? "" : defaultString(order.getOrderNumber(), String.valueOf(order.getId()));
    }

    private String estimatedDeliveryDate() {
        return LocalDate.now().plusDays(5).format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    private String appWebsiteUrl() {
        return "https://kpskrishnai.com";
    }

    private String valueAt(List<String> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return "";
        }
        return safeText(values.get(index));
    }

    private String formatCurrency(BigDecimal amount) {
        return "Rs. " + (amount == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : amount.setScale(2, RoundingMode.HALF_UP));
    }

    private String defaultString(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record TemplateSpec(String name, String language) {
    }
}
