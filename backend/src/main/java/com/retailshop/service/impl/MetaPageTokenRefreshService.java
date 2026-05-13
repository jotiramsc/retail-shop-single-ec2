package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.retailshop.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.UpdateSecretRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
public class MetaPageTokenRefreshService {

    private static final String META_ACCESS_TOKEN_KEY = "META_ACCESS_TOKEN";
    private static final String FB_PAGE_ACCESS_TOKEN_KEY = "FB_PAGE_ACCESS_TOKEN";
    private static final String META_FB_EXCHANGE_TOKEN_KEY = "META_FB_EXCHANGE_TOKEN";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final SecretsManagerClient configuredSecretsManagerClient;

    @Autowired
    public MetaPageTokenRefreshService(AppProperties appProperties, ObjectMapper objectMapper) {
        this(appProperties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build(), null);
    }

    MetaPageTokenRefreshService(AppProperties appProperties,
                                ObjectMapper objectMapper,
                                HttpClient httpClient,
                                SecretsManagerClient secretsManagerClient) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.configuredSecretsManagerClient = secretsManagerClient;
    }

    @Scheduled(
            fixedDelayString = "${app.meta.token-refresh-interval-ms:3888000000}",
            initialDelayString = "${app.meta.token-refresh-initial-delay-ms:60000}"
    )
    public void refreshScheduledMetaPageToken() {
        refreshIfConfigured();
    }

    void refreshIfConfigured() {
        AppProperties.Meta meta = appProperties.getMeta();
        if (meta == null || !meta.isTokenRefreshEnabled()) {
            return;
        }
        if (isBlank(meta.getAppId()) || isBlank(meta.getAppSecret()) || isBlank(meta.getExchangeToken())) {
            log.debug("Meta token refresh skipped because app id, app secret, or exchange token is not configured");
            return;
        }
        if (isBlank(meta.getPageId())) {
            log.warn("Meta token refresh skipped because FB_PAGE_ID is not configured");
            return;
        }

        try {
            String longLivedUserToken = exchangeUserToken(meta);
            String pageAccessToken = fetchPageAccessToken(meta, longLivedUserToken);
            meta.setAccessToken(longLivedUserToken);
            meta.setExchangeToken(longLivedUserToken);
            meta.setPageAccessToken(pageAccessToken);
            persistSecret(meta, longLivedUserToken, pageAccessToken);
            log.info("Meta Facebook Page access token refreshed successfully");
        } catch (Exception exception) {
            log.warn("Meta Facebook Page access token refresh failed: {}", safeMessage(exception));
        }
    }

    private String exchangeUserToken(AppProperties.Meta meta) throws IOException, InterruptedException {
        String url = graphUrl(meta.getGraphVersion(), "oauth/access_token")
                + "?grant_type=fb_exchange_token"
                + "&client_id=" + encode(meta.getAppId())
                + "&client_secret=" + encode(meta.getAppSecret())
                + "&fb_exchange_token=" + encode(meta.getExchangeToken());
        JsonNode payload = executeGet(url, "Unable to exchange Meta access token");
        String token = trimToNull(payload.path("access_token").asText(""));
        if (token == null) {
            throw new IOException("Meta token exchange did not return access_token");
        }
        return token;
    }

    private String fetchPageAccessToken(AppProperties.Meta meta, String userAccessToken) throws IOException, InterruptedException {
        String url = graphUrl(meta.getGraphVersion(), meta.getPageId())
                + "?fields=access_token"
                + "&access_token=" + encode(userAccessToken);
        JsonNode payload = executeGet(url, "Unable to fetch Facebook Page access token");
        String token = trimToNull(payload.path("access_token").asText(""));
        if (token == null) {
            throw new IOException("Facebook Page access token was not returned for the configured page id");
        }
        return token;
    }

    private JsonNode executeGet(String url, String fallbackMessage) throws IOException, InterruptedException {
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

    private void persistSecret(AppProperties.Meta meta,
                               String longLivedUserToken,
                               String pageAccessToken) throws IOException {
        if (isBlank(meta.getTokenRefreshSecretId())) {
            log.info("Meta token refresh updated the running app only; set META_TOKEN_REFRESH_SECRET_ID to persist it");
            return;
        }

        SecretsManagerClient client = configuredSecretsManagerClient;
        boolean closeClient = false;
        if (client == null) {
            client = SecretsManagerClient.builder()
                    .region(Region.of(defaultString(appProperties.getAws().getRegion(), "us-east-1")))
                    .build();
            closeClient = true;
        }
        try {
            String secretId = meta.getTokenRefreshSecretId().trim();
            String currentSecret = client.getSecretValue(GetSecretValueRequest.builder()
                    .secretId(secretId)
                    .build()).secretString();
            ObjectNode root = parseSecretObject(currentSecret);
            root.put(META_ACCESS_TOKEN_KEY, longLivedUserToken);
            root.put(META_FB_EXCHANGE_TOKEN_KEY, longLivedUserToken);
            root.put(FB_PAGE_ACCESS_TOKEN_KEY, pageAccessToken);
            client.updateSecret(UpdateSecretRequest.builder()
                    .secretId(secretId)
                    .secretString(objectMapper.writeValueAsString(root))
                    .build());
        } finally {
            if (closeClient) {
                client.close();
            }
        }
    }

    private ObjectNode parseSecretObject(String secretString) throws IOException {
        if (isBlank(secretString)) {
            return objectMapper.createObjectNode();
        }
        JsonNode parsed = objectMapper.readTree(secretString);
        if (parsed.isTextual()) {
            parsed = objectMapper.readTree(parsed.asText());
        }
        if (!parsed.isObject()) {
            throw new IOException("Configured app secret is not a JSON object");
        }
        return (ObjectNode) parsed;
    }

    private JsonNode parseJson(String payload) throws IOException {
        if (isBlank(payload)) {
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
        return fallback;
    }

    private String graphUrl(String graphVersion, String path) {
        return "https://graph.facebook.com/"
                + defaultString(graphVersion, "v23.0").trim()
                + "/"
                + path.trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(defaultString(value, ""), StandardCharsets.UTF_8);
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safeMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return isBlank(message) ? "unknown Meta token refresh error" : message;
    }
}
