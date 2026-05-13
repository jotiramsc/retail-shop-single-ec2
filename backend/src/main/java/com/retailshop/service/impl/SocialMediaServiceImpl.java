package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.entity.Campaign;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.SocialMediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SocialMediaServiceImpl implements SocialMediaService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public SocialMediaServiceImpl(AppProperties appProperties, ObjectMapper objectMapper) {
        this(appProperties, objectMapper, HttpClient.newHttpClient());
    }

    SocialMediaServiceImpl(AppProperties appProperties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public MarketingChannelResult publishInstagram(Campaign campaign) {
        AppProperties.Meta meta = appProperties.getMeta();
        String instagramToken = facebookAccessToken(meta);
        if (meta == null
                || isBlank(instagramToken)
                || isBlank(meta.getInstagramBusinessAccountId())) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Instagram publishing needs IG_BUSINESS_ACCOUNT_ID and FB_PAGE_ACCESS_TOKEN")
                    .build();
        }
        if (isBlank(campaign.getMediaUrl())) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Instagram publishing requires media")
                    .build();
        }

        try {
            return publishInstagramWithAccount(meta, meta.getInstagramBusinessAccountId(), instagramToken, campaign);
        } catch (IOException exception) {
            String resolvedAccountId = resolveInstagramAccountForRetry(meta, instagramToken, exception);
            if (!isBlank(resolvedAccountId) && !resolvedAccountId.equals(meta.getInstagramBusinessAccountId().trim())) {
                try {
                    return publishInstagramWithAccount(meta, resolvedAccountId, instagramToken, campaign);
                } catch (IOException | InterruptedException retryException) {
                    if (retryException instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    log.warn("Instagram publish retry failed", retryException);
                    return MarketingChannelResult.builder()
                            .success(false)
                            .errorMessage(failureMessage(retryException, "Unable to publish Instagram post"))
                            .build();
                }
            }
            log.warn("Instagram publish failed", exception);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage(failureMessage(exception, "Unable to publish Instagram post"))
                    .build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Instagram publish failed", exception);
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage(failureMessage(exception, "Unable to publish Instagram post"))
                    .build();
        }
    }

    private MarketingChannelResult publishInstagramWithAccount(AppProperties.Meta meta,
                                                              String instagramBusinessAccountId,
                                                              String instagramToken,
                                                              Campaign campaign) throws IOException, InterruptedException {
            JsonNode container = executeMetaPost(
                    buildMetaUrl(meta.getGraphVersion(), instagramBusinessAccountId, "media"),
                    Map.of(
                            "image_url", campaign.getMediaUrl(),
                            "caption", buildCaption(campaign),
                            "access_token", instagramToken
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
            waitForInstagramContainer(meta, creationId, instagramToken);

            JsonNode published = executeMetaPost(
                    buildMetaUrl(meta.getGraphVersion(), instagramBusinessAccountId, "media_publish"),
                    Map.of(
                            "creation_id", creationId,
                            "access_token", instagramToken
                    ),
                    "Unable to publish Instagram post"
            );
            return MarketingChannelResult.builder()
                    .success(true)
                    .responseId(published.path("id").asText(""))
                    .build();
    }

    @Override
    public MarketingChannelResult publishFacebook(Campaign campaign) {
        AppProperties.Meta meta = appProperties.getMeta();
        String facebookToken = facebookAccessToken(meta);
        if (meta == null
                || isBlank(facebookToken)
                || isBlank(meta.getPageId())) {
            return MarketingChannelResult.builder()
                    .success(false)
                    .errorMessage("Facebook publishing needs FB_PAGE_ID and FB_PAGE_ACCESS_TOKEN")
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
                            "access_token", facebookToken
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
                    .errorMessage(failureMessage(exception, "Unable to publish Facebook post"))
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

    private JsonNode executeMetaGet(String url, String fallbackMessage) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("accept", "application/json")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode payload = parseJson(response.body());
        if (response.statusCode() >= 400) {
            throw new IOException(extractError(payload, fallbackMessage));
        }
        return payload;
    }

    private String resolveInstagramAccountForRetry(AppProperties.Meta meta, String token, IOException originalException) {
        if (meta == null || isBlank(meta.getPageId()) || isBlank(token) || !looksLikeObjectPermissionFailure(originalException)) {
            return "";
        }
        try {
            JsonNode page = executeMetaGet(
                    buildMetaObjectUrl(meta.getGraphVersion(), meta.getPageId())
                            + "?"
                            + toFormBody(Map.of("fields", "instagram_business_account", "access_token", token)),
                    "Unable to resolve Instagram business account from Facebook Page"
            );
            String resolvedAccountId = page.path("instagram_business_account").path("id").asText("");
            if (!isBlank(resolvedAccountId)) {
                log.info("Resolved Instagram business account {} from Facebook Page {}", resolvedAccountId, meta.getPageId());
            }
            return resolvedAccountId;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Unable to resolve Instagram business account from Facebook Page {}", meta.getPageId(), exception);
            return "";
        }
    }

    private void waitForInstagramContainer(AppProperties.Meta meta,
                                           String creationId,
                                           String token) throws IOException, InterruptedException {
        IOException lastFailure = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            if (attempt > 0) {
                Thread.sleep(1_500L);
            }
            try {
                JsonNode status = executeMetaGet(
                        buildMetaObjectUrl(meta.getGraphVersion(), creationId)
                                + "?"
                                + toFormBody(Map.of("fields", "status_code,status", "access_token", token)),
                        "Unable to check Instagram media container status"
                );
                String statusCode = status.path("status_code").asText("");
                if ("FINISHED".equalsIgnoreCase(statusCode)) {
                    return;
                }
                if ("ERROR".equalsIgnoreCase(statusCode) || "EXPIRED".equalsIgnoreCase(statusCode)) {
                    String statusMessage = status.path("status").asText("Instagram media container failed");
                    throw new IOException("Instagram media container failed: " + statusMessage);
                }
            } catch (IOException exception) {
                lastFailure = exception;
                if (looksLikeObjectPermissionFailure(exception)) {
                    throw exception;
                }
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IOException("Instagram media is still processing. Please try Publish now again in a few seconds.");
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

    private String failureMessage(Exception exception, String fallback) {
        String message = exception == null ? "" : exception.getMessage();
        return isBlank(message) ? fallback : message;
    }

    private String buildMetaUrl(String graphVersion, String accountId, String path) {
        return buildMetaObjectUrl(graphVersion, accountId)
                + "/" + path;
    }

    private String buildMetaObjectUrl(String graphVersion, String accountId) {
        return "https://graph.facebook.com/" + (isBlank(graphVersion) ? "v23.0" : graphVersion.trim())
                + "/" + accountId.trim();
    }

    private String facebookAccessToken(AppProperties.Meta meta) {
        if (meta == null) {
            return "";
        }
        if (!isBlank(meta.getPageAccessToken())) {
            return meta.getPageAccessToken().trim();
        }
        return isBlank(meta.getAccessToken()) ? "" : meta.getAccessToken().trim();
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
        String publishLink = resolvePublishLink(campaign.getLinkUrl());
        if (!isBlank(publishLink) && !builder.toString().contains(publishLink)) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(visitNowLabel(campaign)).append(": ").append(publishLink);
        }
        if (!isBlank(campaign.getHashtags())) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(campaign.getHashtags().trim());
        }
        return builder.toString().trim();
    }

    private String resolvePublishLink(String rawLink) {
        if (isBlank(rawLink)) {
            return "https://kpskrishnai.com";
        }
        String cleaned = rawLink.trim();
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            return cleaned;
        }
        return "https://kpskrishnai.com";
    }

    private String visitNowLabel(Campaign campaign) {
        return campaign.getLanguage() != null && "MARATHI".equals(campaign.getLanguage().name())
                ? "आता भेट द्या"
                : "Visit now";
    }

    private boolean looksLikeObjectPermissionFailure(Exception exception) {
        String message = exception == null ? "" : exception.getMessage();
        return message != null
                && (message.contains("Unsupported post request")
                || message.contains("does not exist")
                || message.contains("missing permissions"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
