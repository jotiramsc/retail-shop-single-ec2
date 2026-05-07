package com.retailshop.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.MarketingProperties;
import com.retailshop.entity.Campaign;
import com.retailshop.entity.CampaignContent;
import com.retailshop.entity.Customer;
import com.retailshop.enums.CampaignType;
import com.retailshop.enums.MarketingDiscountType;
import com.retailshop.enums.MarketingPlatform;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.SocialPublisher;
import com.retailshop.service.WhatsAppMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppPublisher implements SocialPublisher {

    private static final String GUPSHUP_TEMPLATE_ENDPOINT = "https://api.gupshup.io/wa/api/v1/template/msg";
    private static final DateTimeFormatter OFFER_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final String DEFAULT_WEBSITE_URL = "https://kpskrishnai.com";

    private final MarketingProperties marketingProperties;
    private final WhatsAppMessageService whatsAppMessageService;
    private final com.retailshop.repository.CustomerRepository customerRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public MarketingPlatform platform() {
        return MarketingPlatform.WHATSAPP;
    }

    @Override
    public PublishResult publish(CampaignContent content) {
        String provider = marketingProperties.getWhatsapp().getProvider() == null
                ? "GUPSHUP"
                : marketingProperties.getWhatsapp().getProvider().trim().toUpperCase(Locale.ROOT);
        return switch (provider) {
            case "TWILIO" -> publishViaTwilio(content);
            case "GUPSHUP" -> publishViaGupshup(content);
            default -> new PublishResult(false, null, buildRequestPayload(content, provider), null, "Unsupported WhatsApp provider");
        };
    }

    private PublishResult publishViaTwilio(CampaignContent content) {
        Campaign campaign = new Campaign();
        campaign.setCampaignName(content.getCampaign().getCampaignName());
        campaign.setName(content.getCampaign().getCampaignName());
        campaign.setType(CampaignType.WHATSAPP);
        campaign.setContent(content.getCaptionText());
        campaign.setHashtags(content.getHashtags());
        campaign.setLinkUrl(content.getCampaign().getLinkUrl());
        campaign.setMediaUrl(content.getImageUrl());

        List<Customer> customers = customerRepository.findAll();
        MarketingChannelResult result = whatsAppMessageService.publishCampaign(campaign, customers);
        return new PublishResult(
                result.isSuccess(),
                result.getResponseId(),
                buildRequestPayload(content, "TWILIO"),
                result.getResponseId(),
                result.getErrorMessage()
        );
    }

    private PublishResult publishViaGupshup(CampaignContent content) {
        MarketingProperties.Gupshup gupshup = marketingProperties.getGupshup();
        if (!hasText(gupshup.getApiKey()) || !hasText(gupshup.getAppName()) || !hasText(gupshup.getSourceNumber()) || !hasText(gupshup.getTemplateId())) {
            return new PublishResult(
                    false,
                    null,
                    buildRequestPayload(content, "GUPSHUP"),
                    null,
                    "Gupshup marketing publish needs API key, app name, source number, and template id"
            );
        }

        List<Customer> recipients = customerRepository.findAll().stream()
                .filter(customer -> hasText(customer.getMobile()))
                .toList();
        if (recipients.isEmpty()) {
            return new PublishResult(false, null, buildRequestPayload(content, "GUPSHUP"), null, "No WhatsApp recipients available");
        }

        int submitted = 0;
        String firstMessageId = null;
        String firstError = null;
        for (Customer customer : recipients) {
            try {
                MarketingChannelResult result = sendGupshupTemplate(content, customer, gupshup);
                if (result.isSuccess()) {
                    submitted++;
                    if (firstMessageId == null) {
                        firstMessageId = result.getResponseId();
                    }
                } else if (firstError == null) {
                    firstError = result.getErrorMessage();
                }
            } catch (Exception exception) {
                log.warn("Gupshup marketing publish failed for customer {}", customer.getId(), exception);
                if (firstError == null) {
                    firstError = "Unable to publish via Gupshup";
                }
            }
        }

        boolean success = submitted > 0;
        String responsePayload = "submitted=" + submitted + "/" + recipients.size();
        String errorMessage = success && submitted == recipients.size()
                ? null
                : defaultString(firstError, "Some WhatsApp recipients did not receive the campaign");
        return new PublishResult(success, firstMessageId, buildRequestPayload(content, "GUPSHUP"), responsePayload, errorMessage);
    }

    private MarketingChannelResult sendGupshupTemplate(CampaignContent content,
                                                       Customer customer,
                                                       MarketingProperties.Gupshup gupshup) throws IOException, InterruptedException {
        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("channel", "whatsapp");
        formData.put("source", digitsOnly(gupshup.getSourceNumber()));
        formData.put("destination", "91" + digitsOnly(customer.getMobile()));
        formData.put("src.name", gupshup.getAppName().trim());
        formData.put("template", objectMapper.writeValueAsString(Map.of(
                "id", gupshup.getTemplateId().trim(),
                "params", buildTemplateParams(content, customer)
        )));

        HttpRequest request = HttpRequest.newBuilder(URI.create(GUPSHUP_TEMPLATE_ENDPOINT))
                .POST(HttpRequest.BodyPublishers.ofString(toFormBody(formData)))
                .header("apikey", gupshup.getApiKey().trim())
                .header("content-type", "application/x-www-form-urlencoded")
                .header("accept", "application/json")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode payload = parseJson(response.body());
        if (response.statusCode() >= 400) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage(extractError(payload, "Unable to publish campaign with Gupshup"))
                    .build();
        }

        String status = payload.path("status").asText("");
        String responseId = payload.path("messageId").asText("");
        if (!"submitted".equalsIgnoreCase(status) && responseId.isBlank()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Gupshup did not accept the campaign request")
                    .build();
        }
        return MarketingChannelResult.builder()
                .success(true)
                .responseId(responseId)
                .build();
    }

    private String[] buildTemplateParams(CampaignContent content, Customer customer) {
        Campaign campaign = content.getCampaign();
        String customerName = firstName(customer.getName());
        String discountText = resolveDiscountText(campaign);
        String targetProduct = defaultString(campaign.getOfferTitle(),
                defaultString(campaign.getOfferProduct(), defaultString(campaign.getCampaignName(), "selected collection")));
        String bodyLine = defaultString(content.getCaptionText(), "Elegant designs selected just for you");
        String link = defaultString(campaign.getLinkUrl(), DEFAULT_WEBSITE_URL);
        String validUntil = campaign.getEndDate() == null ? "Limited time" : campaign.getEndDate().format(OFFER_DATE_FORMATTER);
        return new String[]{
                customerName,
                discountText,
                targetProduct,
                bodyLine,
                link,
                validUntil
        };
    }

    private String resolveDiscountText(Campaign campaign) {
        if (campaign.getDiscountType() == null || campaign.getDiscountValue() == null) {
            return "0";
        }
        if (campaign.getDiscountType() == MarketingDiscountType.PERCENTAGE) {
            return campaign.getDiscountValue().stripTrailingZeros().toPlainString();
        }
        if (campaign.getDiscountType() == MarketingDiscountType.FLAT) {
            return campaign.getDiscountValue().stripTrailingZeros().toPlainString();
        }
        return "0";
    }

    private String buildRequestPayload(CampaignContent content, String provider) {
        Campaign campaign = content.getCampaign();
        return "provider=" + provider
                + ";platform=" + content.getPlatform().name()
                + ";campaign=" + safe(campaign.getCampaignName())
                + ";landingUrl=" + safe(campaign.getLinkUrl())
                + ";templateId=" + safe(marketingProperties.getGupshup().getTemplateId());
    }

    private JsonNode parseJson(String body) throws JsonProcessingException {
        if (!hasText(body)) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private String extractError(JsonNode payload, String fallback) {
        String directMessage = payload.path("message").asText("");
        if (hasText(directMessage)) {
            return directMessage;
        }
        String nestedMessage = payload.path("message").path("message").asText("");
        if (hasText(nestedMessage)) {
            return nestedMessage;
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

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultString(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String firstName(String value) {
        if (!hasText(value)) {
            return "Customer";
        }
        String trimmed = value.trim();
        int firstSpace = trimmed.indexOf(' ');
        return firstSpace > 0 ? trimmed.substring(0, firstSpace) : trimmed;
    }
}
