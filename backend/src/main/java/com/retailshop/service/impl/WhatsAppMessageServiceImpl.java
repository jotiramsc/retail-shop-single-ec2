package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.entity.Campaign;
import com.retailshop.entity.Customer;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.WhatsAppMessageService;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WhatsAppMessageServiceImpl implements WhatsAppMessageService {

    private static final String TWILIO_SANDBOX_WHATSAPP_NUMBER = "whatsapp:+14155238886";
    private static final String TWILIO_SANDBOX_OTP_TEMPLATE_NAME = "verifications_2fa_template";
    private static final String TWILIO_AUTHENTICATION_TEMPLATE_TYPE = "whatsapp/authentication";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile OtpTemplateResolution cachedOtpTemplateResolution;

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
        AppProperties.Twilio twilio = appProperties.getTwilio();
        return twilio != null
                && twilio.getAccountSid() != null
                && !twilio.getAccountSid().isBlank()
                && twilio.getAuthToken() != null
                && !twilio.getAuthToken().isBlank()
                && twilio.getWhatsappFrom() != null
                && !twilio.getWhatsappFrom().isBlank();
    }

    @Override
    public MarketingChannelResult sendOtp(String mobile, String otp, long otpTtlMinutes) {
        String body = "Your login code is " + otp + ". It expires in " + otpTtlMinutes + " minutes.";
        OtpTemplateResolution otpTemplate = resolveOtpTemplate();
        Map<String, String> contentVariables = otpTemplate.authenticationTemplate()
                ? Map.of("1", otp)
                : Map.of(
                "1", "login",
                "2", otp
        );
        if (otpTemplate.requiresTemplateButMissing()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Twilio WhatsApp OTP template is not configured")
                    .build();
        }
        return sendSingleMessage(mobile, body, otpTemplate.contentSid(), contentVariables);
    }

    @Override
    public MarketingChannelResult sendOrderUpdate(CustomerOrder order) {
        String body = "Order " + order.getOrderNumber()
                + " is confirmed. Total " + formatCurrency(order.getFinalAmount())
                + ". Payment status: " + safeText(order.getPaymentStatus()) + ".";
        return sendSingleMessage(
                order.getCustomer() != null ? order.getCustomer().getMobile() : "",
                body,
                appProperties.getTwilio().getOrderUpdateContentSid(),
                Map.of(
                        "1", safeText(order.getOrderNumber()),
                        "2", formatCurrency(order.getFinalAmount()),
                        "3", safeText(order.getStatus() != null ? order.getStatus().name() : order.getPaymentStatus()),
                        "4", ""
                )
        );
    }

    @Override
    public MarketingChannelResult broadcastOffer(List<Customer> customers, String content) {
        return broadcast(customers, content, appProperties.getTwilio().getOfferContentSid(), Map.of(
                "1", safeText(content)
        ));
    }

    @Override
    public MarketingChannelResult publishCampaign(Campaign campaign, List<Customer> customers) {
        return broadcast(customers, buildCampaignBody(campaign), appProperties.getTwilio().getOfferContentSid(), Map.of(
                "1", safeText(campaign.getName()),
                "2", safeText(campaign.getContent())
        ));
    }

    private MarketingChannelResult broadcast(List<Customer> customers,
                                             String body,
                                             String contentSid,
                                             Map<String, String> contentVariables) {
        if (!isConfigured()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Twilio WhatsApp sender is not configured")
                    .build();
        }
        List<Customer> recipients = customers == null ? List.of() : customers.stream()
                .filter(customer -> customer.getMobile() != null && !customer.getMobile().isBlank())
                .toList();
        if (recipients.isEmpty()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("No WhatsApp recipients available")
                    .build();
        }

        int sentCount = 0;
        String firstError = null;
        for (Customer customer : recipients) {
            MarketingChannelResult result = sendSingleMessage(customer.getMobile(), body, contentSid, contentVariables);
            if (result.isSuccess()) {
                sentCount++;
            } else if (firstError == null) {
                firstError = result.getErrorMessage();
            }
        }

        return MarketingChannelResult.builder()
                .success(sentCount == recipients.size())
                .responseId("broadcast:sent=" + sentCount + "/" + recipients.size())
                .errorMessage(firstError)
                .build();
    }

    private MarketingChannelResult sendSingleMessage(String mobile,
                                                     String body,
                                                     String contentSid,
                                                     Map<String, String> contentVariables) {
        if (!isConfigured()) {
            log.info("WhatsApp fallback log for {}: {}", mobile, body);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Twilio WhatsApp sender is not configured")
                    .build();
        }

        try {
            AppProperties.Twilio twilio = appProperties.getTwilio();
            Map<String, String> formData = new LinkedHashMap<>();
            formData.put("From", formatWhatsAppAddress(twilio.getWhatsappFrom()));
            formData.put("To", formatWhatsAppAddress(mobile));
            if (contentSid != null && !contentSid.isBlank()) {
                formData.put("ContentSid", contentSid.trim());
                formData.put("ContentVariables", objectMapper.writeValueAsString(contentVariables));
            } else {
                formData.put("Body", body);
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://api.twilio.com/2010-04-01/Accounts/" + twilio.getAccountSid().trim() + "/Messages.json"))
                    .POST(HttpRequest.BodyPublishers.ofString(toFormBody(formData)))
                    .header("authorization", basicAuthHeader())
                    .header("content-type", "application/x-www-form-urlencoded")
                    .header("accept", "application/json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode payload = parseJson(response.body());
            if (response.statusCode() >= 400) {
                return MarketingChannelResult.builder()
                        .success(false)
                        .errorMessage(extractError(payload, "Unable to send WhatsApp message"))
                        .build();
            }
            return MarketingChannelResult.builder()
                    .success(true)
                    .responseId(payload.path("sid").asText(""))
                    .build();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Unable to send WhatsApp message")
                    .build();
        }
    }

    private OtpTemplateResolution resolveOtpTemplate() {
        AppProperties.Twilio twilio = appProperties.getTwilio();
        String configuredSid = safeText(twilio.getOtpContentSid());
        if (!configuredSid.isBlank()) {
            return new OtpTemplateResolution(configuredSid, configuredSid.equals(discoverAuthenticationTemplateSid()), false);
        }

        OtpTemplateResolution cachedResolution = cachedOtpTemplateResolution;
        if (cachedResolution != null && !cachedResolution.contentSid().isBlank()) {
            return cachedResolution;
        }

        String discoveredSid = discoverAuthenticationTemplateSid();
        if (!discoveredSid.isBlank()) {
            OtpTemplateResolution discoveredResolution = new OtpTemplateResolution(discoveredSid, true, false);
            cachedOtpTemplateResolution = discoveredResolution;
            return discoveredResolution;
        }

        if (isSandboxSender()) {
            return new OtpTemplateResolution("", false, true);
        }
        return OtpTemplateResolution.none();
    }

    private String discoverAuthenticationTemplateSid() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://content.twilio.com/v1/Content"))
                    .GET()
                    .header("authorization", basicAuthHeader())
                    .header("accept", "application/json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Unable to discover Twilio sandbox OTP template. HTTP {}", response.statusCode());
                return "";
            }

            JsonNode payload = parseJson(response.body());
            List<JsonNode> contents = new ArrayList<>();
            payload.path("contents").forEach(contents::add);
            for (JsonNode content : contents) {
                JsonNode types = content.path("types");
                boolean matchesFriendlyName = TWILIO_SANDBOX_OTP_TEMPLATE_NAME.equals(content.path("friendly_name").asText(""));
                boolean matchesAuthType = containsTemplateType(types, TWILIO_AUTHENTICATION_TEMPLATE_TYPE);
                if (matchesFriendlyName || matchesAuthType) {
                    return safeText(content.path("sid").asText(""));
                }
            }
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Unable to discover Twilio sandbox OTP template", exception);
        }
        return "";
    }

    private boolean isSandboxSender() {
        return TWILIO_SANDBOX_WHATSAPP_NUMBER.equalsIgnoreCase(formatWhatsAppAddress(appProperties.getTwilio().getWhatsappFrom()));
    }

    private boolean containsTemplateType(JsonNode types, String expectedType) {
        if (types == null || types.isMissingNode() || types.isNull()) {
            return false;
        }
        if (types.isArray()) {
            for (JsonNode type : types) {
                if (expectedType.equals(type.asText(""))) {
                    return true;
                }
            }
            return false;
        }
        return types.has(expectedType);
    }

    private String buildCampaignBody(Campaign campaign) {
        StringBuilder builder = new StringBuilder();
        if (campaign.getName() != null && !campaign.getName().isBlank()) {
            builder.append(campaign.getName().trim());
        }
        if (campaign.getOfferProduct() != null && !campaign.getOfferProduct().isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(campaign.getOfferProduct().trim());
        }
        if (campaign.getContent() != null && !campaign.getContent().isBlank()) {
            if (builder.length() > 0) {
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
        if (payload.hasNonNull("message") && !payload.get("message").asText().isBlank()) {
            return payload.get("message").asText();
        }
        if (payload.hasNonNull("detail") && !payload.get("detail").asText().isBlank()) {
            return payload.get("detail").asText();
        }
        return fallback;
    }

    private String basicAuthHeader() {
        AppProperties.Twilio twilio = appProperties.getTwilio();
        String credentials = twilio.getAccountSid().trim() + ":" + twilio.getAuthToken().trim();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String toFormBody(Map<String, String> formData) {
        return formData.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private String formatWhatsAppAddress(String mobile) {
        String value = safeText(mobile);
        if (value.startsWith("whatsapp:")) {
            return value;
        }
        String digits = value.replaceAll("[^0-9+]", "");
        if (!digits.startsWith("+")) {
            digits = "+91" + digits.substring(Math.max(0, digits.length() - 10));
        }
        return "whatsapp:" + digits;
    }

    private String formatCurrency(BigDecimal amount) {
        return "Rs. " + (amount == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : amount.setScale(2, RoundingMode.HALF_UP));
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record OtpTemplateResolution(String contentSid,
                                         boolean authenticationTemplate,
                                         boolean requiresTemplateButMissing) {
        private static OtpTemplateResolution none() {
            return new OtpTemplateResolution("", false, false);
        }
    }
}
