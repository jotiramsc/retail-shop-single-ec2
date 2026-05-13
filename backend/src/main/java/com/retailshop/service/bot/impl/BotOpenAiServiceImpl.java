package com.retailshop.service.bot.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.BotProperties;
import com.retailshop.dto.bot.BotContext;
import com.retailshop.dto.bot.BotIntentClassification;
import com.retailshop.dto.bot.BotMemoryRecord;
import com.retailshop.enums.WhatsAppBotIntent;
import com.retailshop.service.bot.BotOpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class BotOpenAiServiceImpl implements BotOpenAiService {

    private static final URI CHAT_COMPLETIONS_URI = URI.create("https://api.openai.com/v1/chat/completions");
    private static final URI EMBEDDINGS_URI = URI.create("https://api.openai.com/v1/embeddings");

    private final BotProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BotOpenAiServiceImpl(BotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build();
    }

    @Override
    public Optional<BotIntentClassification> classifyIntent(String message, BotContext context, List<BotMemoryRecord> memories) {
        if (!isChatEnabled()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = chatPayload(0.1, List.of(
                    Map.of("role", "system", "content", """
                            You classify WhatsApp messages for a premium Indian ladies cosmetics and jewellery store.
                            Return strict JSON only with:
                            intent, confidence, category, searchText, minPrice, maxPrice, occasion, orderNumber, needsClarification, clarificationQuestion.
                            Valid intent values:
                            WELCOME_MENU, BROWSE_CATEGORIES, SEARCH_PRODUCTS, PRODUCT_DETAILS, PRODUCT_RECOMMENDATION,
                            OFFERS_AND_COUPONS, ORDER_HISTORY, LATEST_ORDER, ORDER_DETAILS, TOTAL_ORDER_VALUE,
                            ORDER_COUNT, DELIVERY_STATUS, PAYMENT_STATUS, REFUND_STATUS, REORDER, ACCOUNT_HELP,
                            CART_CHECKOUT_HELP, AGENT_HANDOFF, FALLBACK.
                            Correct common spelling mistakes before category/searchText, for example neckalce/neckless -> necklace,
                            earings -> earrings, bangels -> bangles, cosmatic -> cosmetics.
                            Use backend categories if they are relevant. Do not invent order/payment facts.
                            """),
                    Map.of("role", "user", "content", objectMapper.writeValueAsString(Map.of(
                            "message", safe(message),
                            "categories", context == null ? List.of() : nullToList(context.getCategories()),
                            "recentOrderNumbers", recentOrderNumbers(context),
                            "memories", memorySummaries(memories)
                    )))
            ));
            JsonNode root = postJson(CHAT_COMPLETIONS_URI, payload);
            String content = root.path("choices").path(0).path("message").path("content").asText("{}");
            JsonNode classification = objectMapper.readTree(content);
            return Optional.of(BotIntentClassification.builder()
                    .intent(parseIntent(classification.path("intent").asText("FALLBACK")))
                    .confidence(classification.path("confidence").asDouble(0.0))
                    .category(textOrNull(classification.path("category")))
                    .searchText(firstNonBlank(textOrNull(classification.path("searchText")), message))
                    .minPrice(decimalOrNull(classification.path("minPrice")))
                    .maxPrice(decimalOrNull(classification.path("maxPrice")))
                    .occasion(textOrNull(classification.path("occasion")))
                    .orderNumber(textOrNull(classification.path("orderNumber")))
                    .needsClarification(classification.path("needsClarification").asBoolean(false))
                    .clarificationQuestion(textOrNull(classification.path("clarificationQuestion")))
                    .build());
        } catch (Exception exception) {
            log.debug("OpenAI bot intent classification failed", exception);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> generateReply(String message,
                                          BotIntentClassification intent,
                                          BotContext context,
                                          List<BotMemoryRecord> memories,
                                          String factualDraft) {
        if (!isChatEnabled() || !hasText(factualDraft)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = chatPayload(0.35, List.of(
                    Map.of("role", "system", "content", """
                            You polish a WhatsApp bot reply for a premium ladies cosmetics and jewellery store.
                            Keep it concise, warm, and action-oriented.
                            You may improve wording, but you must not change prices, stock, order status, payment status, refund status, links, or any factual values.
                            Do not add facts not present in the factual draft.
                            """),
                    Map.of("role", "user", "content", objectMapper.writeValueAsString(Map.of(
                            "customerMessage", safe(message),
                            "intent", intent == null || intent.getIntent() == null ? "FALLBACK" : intent.getIntent().name(),
                            "factualDraft", factualDraft,
                            "memories", memorySummaries(memories)
                    )))
            ));
            JsonNode root = postJson(CHAT_COMPLETIONS_URI, payload);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return hasText(content) ? Optional.of(content.trim()) : Optional.empty();
        } catch (Exception exception) {
            log.debug("OpenAI bot reply generation failed", exception);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> summarizeMemory(String message, String reply, BotContext context) {
        if (!isChatEnabled() || !hasText(message) || !hasText(reply)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = chatPayload(0.2, List.of(
                    Map.of("role", "system", "content", """
                            Summarize durable customer memory from a WhatsApp retail conversation.
                            Store only useful future sales/support signal: category preference, budget, occasion, product interest, support issue, payment/delivery concern, or purchase pattern.
                            Return one compact sentence. Return empty string if there is no durable memory.
                            """),
                    Map.of("role", "user", "content", objectMapper.writeValueAsString(Map.of(
                            "customerMessage", message,
                            "botReply", reply,
                            "mobile", context == null ? "" : safe(context.getMobile())
                    )))
            ));
            JsonNode root = postJson(CHAT_COMPLETIONS_URI, payload);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return hasText(content) ? Optional.of(content.trim()) : Optional.empty();
        } catch (Exception exception) {
            log.debug("OpenAI memory summarization failed", exception);
            return Optional.empty();
        }
    }

    @Override
    public Optional<List<Double>> embed(String text) {
        if (!properties.isEnabled() || !properties.isMemoryEnabled() || !hasText(apiKey()) || !hasText(text)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", defaultString(properties.getOpenai().getEmbeddingModel(), "text-embedding-3-small"));
            payload.put("input", text);
            JsonNode root = postJson(EMBEDDINGS_URI, payload);
            JsonNode embedding = root.path("data").path(0).path("embedding");
            if (!embedding.isArray()) {
                return Optional.empty();
            }
            List<Double> values = new ArrayList<>();
            embedding.forEach(value -> values.add(value.asDouble()));
            return values.isEmpty() ? Optional.empty() : Optional.of(values);
        } catch (Exception exception) {
            log.debug("OpenAI embedding failed", exception);
            return Optional.empty();
        }
    }

    private Map<String, Object> chatPayload(double temperature, List<Map<String, String>> messages) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", defaultString(properties.getOpenai().getModel(), "gpt-4.1-mini"));
        payload.put("temperature", temperature);
        payload.put("messages", messages);
        return payload;
    }

    private JsonNode postJson(URI uri, Map<String, Object> payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(35))
                .header("authorization", "Bearer " + apiKey())
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("OpenAI request failed with status " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private boolean isChatEnabled() {
        return properties.isEnabled() && properties.isOpenaiEnabled() && hasText(apiKey());
    }

    private String apiKey() {
        return properties.getOpenai() == null ? "" : safe(properties.getOpenai().getApiKey());
    }

    private WhatsAppBotIntent parseIntent(String raw) {
        try {
            return WhatsAppBotIntent.valueOf(defaultString(raw, "FALLBACK").trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return WhatsAppBotIntent.FALLBACK;
        }
    }

    private BigDecimal decimalOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || !node.isNumber() ? null : node.decimalValue();
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return hasText(value) && !"null".equalsIgnoreCase(value) ? value.trim() : null;
    }

    private List<String> recentOrderNumbers(BotContext context) {
        if (context == null || context.getRecentOrders() == null) {
            return List.of();
        }
        return context.getRecentOrders().stream()
                .map(order -> safe(order.getOrderNumber()))
                .filter(this::hasText)
                .limit(5)
                .toList();
    }

    private List<String> memorySummaries(List<BotMemoryRecord> memories) {
        if (memories == null) {
            return List.of();
        }
        return memories.stream()
                .map(BotMemoryRecord::getSummaryText)
                .filter(this::hasText)
                .limit(5)
                .toList();
    }

    private List<String> nullToList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String defaultString(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
