package com.retailshop.service.bot.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.BotProperties;
import com.retailshop.dto.bot.BotMemoryRecord;
import com.retailshop.service.bot.QdrantMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class QdrantMemoryServiceImpl implements QdrantMemoryService {

    private final BotProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile boolean collectionReady;

    public QdrantMemoryServiceImpl(BotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(2, properties.getQdrant().getTimeoutSeconds())))
                .build();
    }

    @Override
    public void ensureCollection() {
        if (collectionReady || !properties.isEnabled() || !properties.isMemoryEnabled()) {
            return;
        }
        try {
            HttpResponse<String> get = send("GET", collectionUri(), null);
            if (get.statusCode() == 200) {
                collectionReady = true;
                return;
            }
            Map<String, Object> payload = Map.of(
                    "vectors", Map.of(
                            "size", properties.getOpenai().getEmbeddingDimensions(),
                            "distance", "Cosine"
                    )
            );
            HttpResponse<String> put = send("PUT", collectionUri(), payload);
            if (put.statusCode() >= 200 && put.statusCode() < 300) {
                collectionReady = true;
            } else {
                log.warn("Qdrant collection creation failed with status {}", put.statusCode());
            }
        } catch (Exception exception) {
            log.debug("Qdrant collection ensure failed", exception);
        }
    }

    @Override
    public List<BotMemoryRecord> searchByMobile(String mobile, List<Double> vector, int limit) {
        if (!properties.isEnabled() || !properties.isMemoryEnabled() || vector == null || vector.isEmpty()) {
            return List.of();
        }
        ensureCollection();
        try {
            Map<String, Object> filter = Map.of(
                    "must", List.of(Map.of("key", "mobile", "match", Map.of("value", normalizeMobile(mobile))))
            );
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("vector", vector);
            payload.put("limit", Math.max(1, limit));
            payload.put("with_payload", true);
            payload.put("filter", filter);
            HttpResponse<String> response = send("POST", collectionUri() + "/points/search", payload);
            if (response.statusCode() >= 400) {
                log.debug("Qdrant search failed with status {}", response.statusCode());
                return List.of();
            }
            JsonNode result = objectMapper.readTree(response.body()).path("result");
            if (!result.isArray()) {
                return List.of();
            }
            java.util.ArrayList<BotMemoryRecord> memories = new java.util.ArrayList<>();
            for (JsonNode point : result) {
                JsonNode payloadNode = point.path("payload");
                memories.add(BotMemoryRecord.builder()
                        .id(point.path("id").asText(null))
                        .customerId(payloadNode.path("customerId").asText(null))
                        .mobile(payloadNode.path("mobile").asText(null))
                        .memoryType(payloadNode.path("memoryType").asText(null))
                        .source(payloadNode.path("source").asText(null))
                        .summaryText(payloadNode.path("summaryText").asText(null))
                        .tags(readStringList(payloadNode.path("tags")))
                        .metadata(Map.of())
                        .createdAt(parseCreatedAt(payloadNode.path("createdAt").asText(null)))
                        .score(point.path("score").asDouble(0.0))
                        .build());
            }
            return memories;
        } catch (Exception exception) {
            log.debug("Qdrant memory search failed", exception);
            return List.of();
        }
    }

    @Override
    public Optional<String> upsert(String pointId, List<Double> vector, BotMemoryRecord memory) {
        if (!properties.isEnabled() || !properties.isMemoryEnabled() || vector == null || vector.isEmpty() || memory == null) {
            return Optional.empty();
        }
        ensureCollection();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("customerId", safe(memory.getCustomerId()));
            payload.put("mobile", normalizeMobile(memory.getMobile()));
            payload.put("memoryType", safe(memory.getMemoryType()));
            payload.put("createdAt", memory.getCreatedAt() == null ? LocalDateTime.now().toString() : memory.getCreatedAt().toString());
            payload.put("source", safe(memory.getSource()));
            payload.put("summaryText", safe(memory.getSummaryText()));
            payload.put("tags", memory.getTags() == null ? List.of() : memory.getTags());

            Map<String, Object> body = Map.of("points", List.of(Map.of(
                    "id", pointId,
                    "vector", vector,
                    "payload", payload
            )));
            HttpResponse<String> response = send("PUT", collectionUri() + "/points?wait=true", body);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Optional.of(pointId);
            }
            log.debug("Qdrant upsert failed with status {}", response.statusCode());
            return Optional.empty();
        } catch (Exception exception) {
            log.debug("Qdrant memory upsert failed", exception);
            return Optional.empty();
        }
    }

    private HttpResponse<String> send(String method, String uri, Map<String, Object> payload) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(Math.max(2, properties.getQdrant().getTimeoutSeconds())))
                .header("content-type", "application/json");
        if (hasText(properties.getQdrant().getApiKey())) {
            builder.header("api-key", properties.getQdrant().getApiKey().trim());
        }
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(payload == null ? "" : objectMapper.writeValueAsString(payload)));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String collectionUri() {
        return baseUri() + "/collections/" + properties.getQdrant().getCollectionCustomerMemory();
    }

    private String baseUri() {
        String scheme = properties.getQdrant().isUseTls() ? "https" : "http";
        return scheme + "://" + properties.getQdrant().getHost() + ":" + properties.getQdrant().getPort();
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        node.forEach(item -> {
            if (hasText(item.asText(null))) {
                values.add(item.asText().trim());
            }
        });
        return values;
    }

    private LocalDateTime parseCreatedAt(String value) {
        try {
            return hasText(value) ? LocalDateTime.parse(value) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeMobile(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        return digits.length() <= 10 ? digits : digits.substring(digits.length() - 10);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
