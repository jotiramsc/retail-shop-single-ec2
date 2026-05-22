package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.MarketingProperties;
import com.retailshop.entity.Product;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.ProductAiDescriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAiDescriptionServiceImpl implements ProductAiDescriptionService {

    private static final int MAX_ERROR_LENGTH = 900;

    private final ProductRepository productRepository;
    private final MarketingProperties marketingProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Override
    @Transactional
    public void queueDescriptionGeneration(UUID productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return;
        }
        product.setAiDescriptionStatus("PENDING");
        product.setAiDescriptionError(null);
        productRepository.save(product);

        Runnable task = () -> CompletableFuture.runAsync(() -> generateAndSave(productId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private void generateAndSave(UUID productId) {
        try {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) {
                return;
            }
            if (marketingProperties == null
                    || marketingProperties.getAi() == null
                    || !marketingProperties.getAi().isEnabled()
                    || isBlank(marketingProperties.getAi().getApiKey())) {
                markFailed(product, "OpenAI is not configured.");
                return;
            }
            String description = requestDescription(product);
            product.setAiDescriptionStatus("GENERATED");
            product.setAiDescription(trimDescription(description));
            product.setAiDescriptionGeneratedAt(LocalDateTime.now());
            product.setAiDescriptionError(null);
            productRepository.save(product);
        } catch (Exception exception) {
            log.warn("AI product description generation failed for product {}", productId, exception);
            productRepository.findById(productId).ifPresent(product -> markFailed(product, exception.getMessage()));
        }
    }

    private String requestDescription(Product product) throws IOException, InterruptedException {
        String systemPrompt = """
                You write concise ecommerce product descriptions for an Indian jewellery and cosmetics shop.
                Return plain text only. Keep it premium, simple, customer-friendly, and sales-focused without inventing facts.
                Maximum 10 lines total. Use one short attractive paragraph followed by 3 to 5 bullet points.
                Use bullet character "•" for each bullet.
                """;
        String userPrompt = """
                Product name: %s
                Category: %s
                Shop price: %s
                Website price: %s
                Manual description: %s

                Write a polished customer-facing description. Mention material, color, occasion, or style only if present in the product data.
                """.formatted(
                defaultString(product.getName(), "Product"),
                defaultString(product.getCategory(), "Krishnai collection"),
                formatPrice(product.getSellingPrice()),
                formatPrice(product.getResolvedWebsitePrice()),
                defaultString(product.getDescription(), "Not provided")
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", defaultString(marketingProperties.getAi().getModel(), "gpt-4.1-mini"));
        payload.put("temperature", 0.55);
        payload.put("max_tokens", 260);
        payload.put("messages", java.util.List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("authorization", "Bearer " + marketingProperties.getAi().getApiKey().trim())
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException(extractApiErrorMessage(response.body()));
        }
        JsonNode content = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
        String generated = content.asText("").trim();
        if (generated.isBlank()) {
            throw new IOException("OpenAI returned an empty product description.");
        }
        return generated;
    }

    private void markFailed(Product product, String errorMessage) {
        product.setAiDescriptionStatus("FAILED");
        product.setAiDescriptionError(truncate(defaultString(errorMessage, "AI description generation failed."), MAX_ERROR_LENGTH));
        productRepository.save(product);
    }

    private String trimDescription(String value) {
        String[] lines = defaultString(value, "")
                .replace("\r", "")
                .lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .limit(10)
                .toArray(String[]::new);
        return String.join("\n", lines);
    }

    private String extractApiErrorMessage(String responseBody) {
        try {
            String message = objectMapper.readTree(responseBody).path("error").path("message").asText("");
            return message == null || message.isBlank() ? "OpenAI description generation failed." : message;
        } catch (IOException ignored) {
            return "OpenAI description generation failed.";
        }
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "Not provided" : "₹" + price.stripTrailingZeros().toPlainString();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.trim().isBlank() ? fallback : value.trim();
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
