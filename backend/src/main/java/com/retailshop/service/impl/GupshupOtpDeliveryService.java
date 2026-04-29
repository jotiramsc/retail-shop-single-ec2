package com.retailshop.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.OtpDeliveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GupshupOtpDeliveryService implements OtpDeliveryService {

    private static final String GUPSHUP_TEMPLATE_ENDPOINT = "https://api.gupshup.io/wa/api/v1/template/msg";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public GupshupOtpDeliveryService(AppProperties appProperties, ObjectMapper objectMapper) {
        this(appProperties, objectMapper, HttpClient.newHttpClient());
    }

    GupshupOtpDeliveryService(AppProperties appProperties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public boolean isConfigured() {
        AppProperties.Gupshup gupshup = appProperties.getGupshup();
        return hasText(gupshup.getApiKey())
                && hasText(gupshup.getAppName())
                && hasText(gupshup.getSourceNumber())
                && hasText(gupshup.getOtpTemplateId());
    }

    @Override
    public MarketingChannelResult sendOtp(String mobile, String otp, long otpTtlMinutes) {
        if (!isConfigured()) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Gupshup OTP is not fully configured")
                    .build();
        }

        try {
            AppProperties.Gupshup gupshup = appProperties.getGupshup();
            Map<String, String> formData = new LinkedHashMap<>();
            formData.put("channel", "whatsapp");
            formData.put("source", digitsOnly(gupshup.getSourceNumber()));
            formData.put("destination", "91" + digitsOnly(mobile));
            formData.put("src.name", gupshup.getAppName().trim());
            formData.put("template", objectMapper.writeValueAsString(Map.of(
                    "id", gupshup.getOtpTemplateId().trim(),
                    "params", new String[]{otp}
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
                        .errorMessage(extractError(payload, "Unable to send OTP with Gupshup"))
                        .build();
            }

            String status = payload.path("status").asText("");
            String responseId = payload.path("messageId").asText("");
            if (!"submitted".equalsIgnoreCase(status) && responseId.isBlank()) {
                return MarketingChannelResult.builder()
                        .success(false)
                        .errorMessage("Gupshup did not accept the OTP request")
                        .build();
            }

            return MarketingChannelResult.builder()
                    .success(true)
                    .responseId(responseId)
                    .build();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Unable to send OTP with Gupshup")
                    .build();
        }
    }

    @Override
    public String getChannel() {
        return "WHATSAPP";
    }

    private JsonNode parseJson(String body) throws JsonProcessingException {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private String extractError(JsonNode payload, String fallback) {
        String directMessage = payload.path("message").asText("");
        if (!directMessage.isBlank()) {
            return directMessage;
        }
        String nestedMessage = payload.path("message").path("message").asText("");
        if (!nestedMessage.isBlank()) {
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }
}
