package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.entity.Campaign;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.SocialMediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialMediaServiceImpl implements SocialMediaService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public MarketingChannelResult publishInstagram(Campaign campaign) {
        AppProperties.Meta meta = appProperties.getMeta();
        if (meta == null
                || isBlank(meta.getAccessToken())
                || isBlank(meta.getInstagramBusinessAccountId())) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Instagram publishing is not configured")
                    .build();
        }
        if (isBlank(campaign.getMediaUrl())) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Instagram publishing requires media")
                    .build();
        }

        try {
            JsonNode container = executeMetaPost(
                    buildMetaUrl(meta.getGraphVersion(), meta.getInstagramBusinessAccountId(), "media"),
                    Map.of(
                            "image_url", campaign.getMediaUrl(),
                            "caption", buildCaption(campaign),
                            "access_token", meta.getAccessToken().trim()
                    ),
                    "Unable to create Instagram media container"
            );
            String creationId = container.path("id").asText("");
            if (creationId.isBlank()) {
                return MarketingChannelResult.builder()
                        .success(false)
                        .errorMessage("Instagram media container id was not returned")
                        .build();
            }

            JsonNode published = executeMetaPost(
                    buildMetaUrl(meta.getGraphVersion(), meta.getInstagramBusinessAccountId(), "media_publish"),
                    Map.of(
                            "creation_id", creationId,
                            "access_token", meta.getAccessToken().trim()
                    ),
                    "Unable to publish Instagram post"
            );
            return MarketingChannelResult.builder()
                    .success(true)
                    .responseId(published.path("id").asText(""))
                    .build();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Instagram publish failed", exception);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Unable to publish Instagram post")
                    .build();
        }
    }

    @Override
    public MarketingChannelResult publishFacebook(Campaign campaign) {
        AppProperties.Meta meta = appProperties.getMeta();
        if (meta == null
                || isBlank(meta.getAccessToken())
                || isBlank(meta.getPageId())) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Facebook publishing is not configured")
                    .build();
        }
        if (isBlank(campaign.getMediaUrl())) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Facebook publishing requires media")
                    .build();
        }

        try {
            JsonNode published = executeMetaPost(
                    buildMetaUrl(meta.getGraphVersion(), meta.getPageId(), "photos"),
                    Map.of(
                            "url", campaign.getMediaUrl(),
                            "caption", buildCaption(campaign),
                            "published", "true",
                            "access_token", meta.getAccessToken().trim()
                    ),
                    "Unable to publish Facebook post"
            );
            return MarketingChannelResult.builder()
                    .success(true)
                    .responseId(published.path("post_id").asText(published.path("id").asText("")))
                    .build();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Facebook publish failed", exception);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Unable to publish Facebook post")
                    .build();
        }
    }

    private JsonNode executeMetaPost(String url,
                                     Map<String, String> formData,
                                     String fallbackMessage) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(toFormBody(formData)))
                .header("content-type", "application/x-www-form-urlencoded")
                .header("accept", "application/json")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode payload = parseJson(response.body());
        if (response.statusCode() >= 400) {
            throw new IOException(extractError(payload, fallbackMessage));
        }
        return payload;
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
        JsonNode error = payload.path("error");
        if (error.hasNonNull("message") && !error.get("message").asText().isBlank()) {
            return error.get("message").asText();
        }
        if (payload.hasNonNull("message") && !payload.get("message").asText().isBlank()) {
            return payload.get("message").asText();
        }
        return fallback;
    }

    private String buildMetaUrl(String graphVersion, String accountId, String path) {
        return "https://graph.facebook.com/" + (isBlank(graphVersion) ? "v23.0" : graphVersion.trim())
                + "/" + accountId.trim()
                + "/" + path;
    }

    private String toFormBody(Map<String, String> formData) {
        return formData.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private String buildCaption(Campaign campaign) {
        StringBuilder builder = new StringBuilder();
        if (!isBlank(campaign.getContent())) {
            builder.append(campaign.getContent().trim());
        }
        if (!isBlank(campaign.getHashtags())) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(campaign.getHashtags().trim());
        }
        if (!isBlank(campaign.getLinkUrl())) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(campaign.getLinkUrl().trim());
        }
        return builder.toString().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
