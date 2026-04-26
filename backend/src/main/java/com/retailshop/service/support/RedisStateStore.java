package com.retailshop.service.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.dto.CartItemResponse;
import com.retailshop.dto.CartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisStateStore {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public Optional<OtpCacheEntry> getOtp(String mobile) {
        return readJson(keyForOtp(mobile), OtpCacheEntry.class);
    }

    public void putOtp(String mobile, OtpCacheEntry entry, long ttlMinutes) {
        writeJson(keyForOtp(mobile), entry, ttlMinutes, TimeUnit.MINUTES);
    }

    public void deleteOtp(String mobile) {
        delete(keyForOtp(mobile));
    }

    public Optional<CartResponse> getCart(UUID customerId) {
        return readJson(keyForCart(customerId), CartCacheEntry.class).map(CartCacheEntry::toResponse);
    }

    public void putCart(UUID customerId, CartResponse response) {
        writeJson(
                keyForCart(customerId),
                CartCacheEntry.from(response),
                appProperties.getRedis().getCartTtlMinutes(),
                TimeUnit.MINUTES
        );
    }

    public void deleteCart(UUID customerId) {
        delete(keyForCart(customerId));
    }

    private String keyForOtp(String mobile) {
        return appProperties.getRedis().getOtpKeyPrefix() + mobile;
    }

    private String keyForCart(UUID customerId) {
        return appProperties.getRedis().getCartKeyPrefix() + customerId;
    }

    private <T> Optional<T> readJson(String key, Class<T> type) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                return Optional.empty();
            }
            String payload = redisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(payload, type));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private void writeJson(String key, Object value, long ttl, TimeUnit unit) {
        if (!isEnabled()) {
            return;
        }
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                return;
            }
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl, unit);
        } catch (Exception ignored) {
        }
    }

    private void delete(String key) {
        if (!isEnabled()) {
            return;
        }
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate != null) {
                redisTemplate.delete(key);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isEnabled() {
        return appProperties.getRedis() != null && appProperties.getRedis().isEnabled();
    }

    public record OtpCacheEntry(String otp, Instant sentAt, Instant expiresAt) {
    }

    public static class CartCacheEntry {
        private final List<CartItemCacheEntry> items;
        private final BigDecimal subtotal;

        @JsonCreator
        public CartCacheEntry(@JsonProperty("items") List<CartItemCacheEntry> items,
                              @JsonProperty("subtotal") BigDecimal subtotal) {
            this.items = items == null ? List.of() : items;
            this.subtotal = subtotal == null ? BigDecimal.ZERO : subtotal;
        }

        public static CartCacheEntry from(CartResponse response) {
            return new CartCacheEntry(
                    response.getItems() == null ? List.of() : response.getItems().stream().map(CartItemCacheEntry::from).toList(),
                    response.getSubtotal()
            );
        }

        public CartResponse toResponse() {
            return CartResponse.builder()
                    .items(items.stream().map(CartItemCacheEntry::toResponse).toList())
                    .subtotal(subtotal)
                    .build();
        }

        public List<CartItemCacheEntry> getItems() {
            return items;
        }

        public BigDecimal getSubtotal() {
            return subtotal;
        }
    }

    public static class CartItemCacheEntry {
        private final UUID productId;
        private final String name;
        private final String sku;
        private final String category;
        private final String imageDataUrl;
        private final BigDecimal price;
        private final Integer quantity;
        private final Integer stockAvailable;
        private final BigDecimal lineTotal;

        @JsonCreator
        public CartItemCacheEntry(@JsonProperty("productId") UUID productId,
                                  @JsonProperty("name") String name,
                                  @JsonProperty("sku") String sku,
                                  @JsonProperty("category") String category,
                                  @JsonProperty("imageDataUrl") String imageDataUrl,
                                  @JsonProperty("price") BigDecimal price,
                                  @JsonProperty("quantity") Integer quantity,
                                  @JsonProperty("stockAvailable") Integer stockAvailable,
                                  @JsonProperty("lineTotal") BigDecimal lineTotal) {
            this.productId = productId;
            this.name = name;
            this.sku = sku;
            this.category = category;
            this.imageDataUrl = imageDataUrl;
            this.price = price;
            this.quantity = quantity;
            this.stockAvailable = stockAvailable;
            this.lineTotal = lineTotal;
        }

        public static CartItemCacheEntry from(CartItemResponse response) {
            return new CartItemCacheEntry(
                    response.getProductId(),
                    response.getName(),
                    response.getSku(),
                    response.getCategory(),
                    response.getImageDataUrl(),
                    response.getPrice(),
                    response.getQuantity(),
                    response.getStockAvailable(),
                    response.getLineTotal()
            );
        }

        public CartItemResponse toResponse() {
            return CartItemResponse.builder()
                    .productId(productId)
                    .name(name)
                    .sku(sku)
                    .category(category)
                    .imageDataUrl(imageDataUrl)
                    .price(price)
                    .quantity(quantity)
                    .stockAvailable(stockAvailable)
                    .lineTotal(lineTotal)
                    .build();
        }

        public UUID getProductId() {
            return productId;
        }

        public String getName() {
            return name;
        }

        public String getSku() {
            return sku;
        }

        public String getCategory() {
            return category;
        }

        public String getImageDataUrl() {
            return imageDataUrl;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public Integer getStockAvailable() {
            return stockAvailable;
        }

        public BigDecimal getLineTotal() {
            return lineTotal;
        }
    }
}
