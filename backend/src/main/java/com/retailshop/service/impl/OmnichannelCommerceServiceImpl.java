package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.OmnichannelProperties;
import com.retailshop.dto.OmnichannelLeadRequest;
import com.retailshop.dto.OmnichannelLeadResponse;
import com.retailshop.dto.OmnichannelProductCardResponse;
import com.retailshop.dto.OmnichannelProductSearchRequest;
import com.retailshop.dto.OmnichannelProductSearchResponse;
import com.retailshop.dto.OmnichannelWebhookResponse;
import com.retailshop.entity.AiRecommendationLog;
import com.retailshop.entity.OmnichannelConversation;
import com.retailshop.entity.OmnichannelConversationMessage;
import com.retailshop.entity.OmnichannelLead;
import com.retailshop.entity.Product;
import com.retailshop.entity.SocialWebhookEvent;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.AiRecommendationLogRepository;
import com.retailshop.repository.OmnichannelConversationMessageRepository;
import com.retailshop.repository.OmnichannelConversationRepository;
import com.retailshop.repository.OmnichannelLeadRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.repository.SocialWebhookEventRepository;
import com.retailshop.service.OmnichannelCommerceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OmnichannelCommerceServiceImpl implements OmnichannelCommerceService {

    private final OmnichannelProperties omnichannelProperties;
    private final ProductRepository productRepository;
    private final OmnichannelLeadRepository leadRepository;
    private final OmnichannelConversationRepository conversationRepository;
    private final OmnichannelConversationMessageRepository messageRepository;
    private final SocialWebhookEventRepository webhookEventRepository;
    private final AiRecommendationLogRepository recommendationLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public OmnichannelLeadResponse captureLead(OmnichannelLeadRequest request) {
        String channel = normalizeChannel(request.getChannel());
        String externalUserId = trimToNull(firstNonBlank(
                request.getExternalUserId(),
                request.getExternalId(),
                request.getCustomerHandleOrPhone()
        ));
        String messageText = firstNonBlank(request.getMessageText(), request.getQuery());
        String sourceCampaign = firstNonBlank(request.getSourceCampaign(), request.getCampaignName());
        String productInterest = firstNonBlank(request.getProductInterest(), buildProductInterest(request));
        String mobile = firstNonBlank(
                normalizeMobile(request.getMobile()),
                normalizeMobile(request.getCustomerHandleOrPhone()),
                normalizeMobile(externalUserId)
        );
        String externalThreadId = firstNonBlank(request.getExternalThreadId(), request.getSourceMessageId(), externalUserId);
        OmnichannelLead lead = externalUserId == null
                ? new OmnichannelLead()
                : leadRepository.findFirstByChannelAndExternalUserIdOrderByUpdatedAtDesc(channel, externalUserId)
                .orElseGet(OmnichannelLead::new);

        lead.setChannel(channel);
        lead.setExternalUserId(externalUserId);
        lead.setCustomerName(firstNonBlank(request.getCustomerName(), lead.getCustomerName()));
        lead.setMobile(firstNonBlank(mobile, lead.getMobile()));
        lead.setSourceCampaign(firstNonBlank(sourceCampaign, lead.getSourceCampaign()));
        lead.setProductInterest(firstNonBlank(productInterest, lead.getProductInterest()));
        lead.setLatestMessage(firstNonBlank(messageText, lead.getLatestMessage()));
        lead.setStatus("NEW");
        OmnichannelLead savedLead = leadRepository.save(lead);

        OmnichannelConversation conversation = conversationRepository.findFirstByLead_IdAndChannelOrderByUpdatedAtDesc(savedLead.getId(), channel)
                .orElseGet(() -> {
                    OmnichannelConversation created = new OmnichannelConversation();
                    created.setLead(savedLead);
                    created.setChannel(channel);
                    created.setExternalThreadId(externalThreadId);
                    return created;
                });
        conversation.setExternalThreadId(firstNonBlank(externalThreadId, conversation.getExternalThreadId(), externalUserId));
        conversation.setStatus("OPEN");
        OmnichannelConversation savedConversation = conversationRepository.save(conversation);

        String sourceMessageId = trimToNull(request.getSourceMessageId());
        boolean duplicateInbound = sourceMessageId != null
                && messageRepository.existsByConversation_IdAndDirectionAndExternalMessageId(savedConversation.getId(), "INBOUND", sourceMessageId);
        if (!duplicateInbound && (hasText(messageText) || hasText(request.getRawPayload()))) {
            OmnichannelConversationMessage message = new OmnichannelConversationMessage();
            message.setConversation(savedConversation);
            message.setDirection("INBOUND");
            message.setMessageType("TEXT");
            message.setMessageText(trimToNull(messageText));
            message.setRawPayload(truncate(trimToNull(request.getRawPayload()), 12000));
            message.setExternalMessageId(sourceMessageId);
            message.setCorrelationId(trimToNull(request.getCorrelationId()));
            messageRepository.save(message);
        }

        return mapLead(savedLead);
    }

    @Override
    @Transactional
    public OmnichannelProductSearchResponse searchProducts(OmnichannelProductSearchRequest request) {
        int limit = resolveLimit(request.getLimit());
        String normalizedQuery = normalizeText(request.getQuery());
        String normalizedCategory = normalizeText(request.getCategory());
        String normalizedOccasion = normalizeText(request.getOccasion());
        boolean inStockOnly = request.getInStockOnly() == null || request.getInStockOnly();
        List<UUID> excludeIds = request.getExcludeProductIds() == null ? List.of() : request.getExcludeProductIds();

        List<Product> matches = productRepository.findAll().stream()
                .filter(product -> excludeIds.isEmpty() || !excludeIds.contains(product.getId()))
                .filter(product -> !inStockOnly || isInStock(product))
                .filter(product -> matchesCategory(product, normalizedCategory))
                .filter(product -> matchesPrice(product, request.getMinPrice(), request.getMaxPrice()))
                .filter(product -> matchesText(product, normalizedQuery, normalizedOccasion))
                .sorted(Comparator
                        .comparing((Product product) -> Boolean.TRUE.equals(product.getShowInFeaturedPieces())).reversed()
                        .thenComparing((Product product) -> Boolean.TRUE.equals(product.getShowInNewRelease())).reversed()
                        .thenComparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<OmnichannelProductCardResponse> cards = matches.stream()
                .limit(limit)
                .map(product -> mapProductCard(product, request))
                .toList();

        logRecommendation(request, cards);

        return OmnichannelProductSearchResponse.builder()
                .query(firstNonBlank(request.getQuery(), request.getCategory(), request.getOccasion(), "Recommended products"))
                .totalMatches(matches.size())
                .introMessage(buildIntroMessage(cards, request))
                .products(cards)
                .build();
    }

    @Override
    public String verifyMetaWebhook(String mode, String verifyToken, String challenge) {
        if (!"subscribe".equalsIgnoreCase(mode)) {
            throw new BusinessException("Unsupported webhook verification mode");
        }
        String expected = trimToNull(omnichannelProperties.getWebhookVerifyToken());
        if (expected == null || !expected.equals(verifyToken)) {
            throw new BusinessException("Invalid webhook verification token");
        }
        return challenge == null ? "" : challenge;
    }

    @Override
    @Transactional
    public OmnichannelWebhookResponse receiveMetaWebhook(String payload, String signature) {
        SocialWebhookEvent event = new SocialWebhookEvent();
        Boolean signatureValid = validateWebhookSignature(payload, signature);
        event.setProvider("META");
        event.setEventType(resolveEventType(payload));
        event.setExternalEventId(resolveExternalEventId(payload));
        event.setSignatureValid(signatureValid);
        event.setRawPayload(truncate(payload, 12000));
        webhookEventRepository.save(event);

        if (Boolean.FALSE.equals(signatureValid)) {
            return OmnichannelWebhookResponse.builder()
                    .accepted(false)
                    .message("Webhook stored but signature validation failed")
                    .build();
        }

        OmnichannelLeadResponse lead = extractLeadFromPayload(payload);
        return OmnichannelWebhookResponse.builder()
                .accepted(true)
                .message(lead == null ? "Webhook stored" : "Webhook stored and lead captured")
                .leadId(lead == null ? null : lead.getId())
                .build();
    }

    private OmnichannelLeadResponse extractLeadFromPayload(String payload) {
        if (!hasText(payload)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode messaging = root.path("entry").path(0).path("messaging").path(0);
            if (messaging.isMissingNode() || messaging.isNull()) {
                return null;
            }
            OmnichannelLeadRequest request = new OmnichannelLeadRequest();
            request.setChannel("META");
            request.setExternalUserId(messaging.path("sender").path("id").asText(null));
            request.setExternalThreadId(messaging.path("recipient").path("id").asText(null));
            request.setMessageText(firstNonBlank(
                    messaging.path("message").path("text").asText(null),
                    messaging.path("postback").path("title").asText(null),
                    messaging.path("referral").path("ref").asText(null)
            ));
            request.setSourceCampaign(messaging.path("referral").path("ad_id").asText(null));
            request.setProductInterest(messaging.path("referral").path("product").asText(null));
            request.setRawPayload(payload);
            return captureLead(request);
        } catch (Exception ignored) {
            return null;
        }
    }

    private OmnichannelProductCardResponse mapProductCard(Product product, OmnichannelProductSearchRequest request) {
        BigDecimal price = product.getResolvedWebsitePrice();
        boolean inStock = isInStock(product);
        String source = firstNonBlank(request.getSource(), request.getChannel(), "whatsapp");
        String campaign = firstNonBlank(request.getCampaign(), "ai-commerce");
        String couponCode = firstNonBlank(request.getCouponCode(), omnichannelProperties.getDefaultCouponCode());
        String baseProductUrl = UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path("/products")
                .queryParam("productId", product.getId())
                .queryParam("q", product.getName())
                .queryParam("utm_source", source)
                .queryParam("utm_medium", "ai_commerce")
                .queryParam("utm_campaign", campaign)
                .build()
                .toUriString();
        String buyNowUrl = UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path("/products")
                .queryParam("autoAdd", product.getId())
                .queryParam("redirect", "cart")
                .queryParamIfPresent("coupon", java.util.Optional.ofNullable(trimToNull(couponCode)))
                .queryParam("utm_source", source)
                .queryParam("utm_medium", "ai_commerce")
                .queryParam("utm_campaign", campaign)
                .build()
                .toUriString();
        String checkoutUrl = UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path("/products")
                .queryParam("autoAdd", product.getId())
                .queryParam("redirect", "checkout")
                .queryParamIfPresent("coupon", java.util.Optional.ofNullable(trimToNull(couponCode)))
                .queryParam("utm_source", source)
                .queryParam("utm_medium", "ai_commerce")
                .queryParam("utm_campaign", campaign)
                .build()
                .toUriString();

        return OmnichannelProductCardResponse.builder()
                .productId(product.getId())
                .name(product.getName())
                .category(product.getCategory())
                .sku(product.getSku())
                .price(price)
                .quantity(product.getQuantity())
                .inStock(inStock)
                .stockLabel(stockLabel(product))
                .imageUrl(product.getImageDataUrl())
                .shortBenefit(buildShortBenefit(product, request.getOccasion()))
                .productUrl(baseProductUrl)
                .buyNowUrl(buyNowUrl)
                .checkoutUrl(checkoutUrl)
                .build();
    }

    private void logRecommendation(OmnichannelProductSearchRequest request, List<OmnichannelProductCardResponse> cards) {
        AiRecommendationLog log = new AiRecommendationLog();
        log.setLeadId(request.getLeadId());
        log.setChannel(normalizeChannel(firstNonBlank(request.getChannel(), request.getSource(), "WHATSAPP")));
        log.setSearchQuery(firstNonBlank(request.getQuery(), request.getCategory(), request.getOccasion(), ""));
        log.setFilters("category=" + safe(request.getCategory()) + ";minPrice=" + safe(request.getMinPrice()) + ";maxPrice=" + safe(request.getMaxPrice()) + ";occasion=" + safe(request.getOccasion()));
        log.setRecommendedProductIds(cards.stream().map(card -> card.getProductId().toString()).collect(Collectors.joining(",")));
        recommendationLogRepository.save(log);
    }

    private String buildIntroMessage(List<OmnichannelProductCardResponse> cards, OmnichannelProductSearchRequest request) {
        if (cards.isEmpty()) {
            return "I could not find an exact match. Please share your budget or occasion and I will suggest closer options.";
        }
        String context = firstNonBlank(request.getOccasion(), request.getQuery(), request.getCategory(), "your selection");
        return "Here are " + cards.size() + " options for " + context + ". Tap Buy Now to continue on the website.";
    }

    private boolean matchesText(Product product, String query, String occasion) {
        if (!hasText(query) && !hasText(occasion)) {
            return true;
        }
        String haystack = normalizeText(product.getName() + " " + product.getCategory() + " " + product.getSku());
        return words(query + " " + occasion).stream().anyMatch(haystack::contains);
    }

    private boolean matchesCategory(Product product, String category) {
        if (!hasText(category)) {
            return true;
        }
        String nCat = normalizeText(category);
        String pCat = normalizeText(product.getCategory());
        String pName = normalizeText(product.getName());
        if (!hasText(nCat)) {
            return true;
        }
        if (hasText(pCat) && (pCat.contains(nCat) || nCat.contains(pCat))) {
            return true;
        }
        if (hasText(pName) && pName.contains(nCat)) {
            return true;
        }
        String haystack = pCat + " " + pName;
        for (String token : words(category)) {
            if (hasText(token) && haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPrice(Product product, BigDecimal minPrice, BigDecimal maxPrice) {
        BigDecimal price = product.getResolvedWebsitePrice();
        if (price == null) {
            return false;
        }
        return (minPrice == null || price.compareTo(minPrice) >= 0)
                && (maxPrice == null || price.compareTo(maxPrice) <= 0);
    }

    private String buildShortBenefit(Product product, String occasion) {
        if (hasText(occasion)) {
            return "Elegant pick for " + occasion.trim();
        }
        if (normalizeText(product.getName()).contains("bridal")) {
            return "Beautiful choice for wedding looks";
        }
        if (normalizeText(product.getName()).contains("pearl")) {
            return "Classic pearl style for gifting and occasions";
        }
        return "Ready-to-order style with live stock visibility";
    }

    private String stockLabel(Product product) {
        int quantity = product.getQuantity() == null ? 0 : product.getQuantity();
        if (quantity <= 0) {
            return "Out of stock";
        }
        int threshold = product.getLowStockThreshold() == null ? 5 : product.getLowStockThreshold();
        return quantity <= Math.max(1, threshold) ? "Last few remaining" : "Available now";
    }

    private boolean isInStock(Product product) {
        return product.getQuantity() != null && product.getQuantity() > 0;
    }

    private OmnichannelLeadResponse mapLead(OmnichannelLead lead) {
        return OmnichannelLeadResponse.builder()
                .id(lead.getId())
                .leadId(lead.getId())
                .channel(lead.getChannel())
                .externalUserId(lead.getExternalUserId())
                .customerName(lead.getCustomerName())
                .mobile(lead.getMobile())
                .sourceCampaign(lead.getSourceCampaign())
                .productInterest(lead.getProductInterest())
                .latestMessage(lead.getLatestMessage())
                .status(lead.getStatus())
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .build();
    }

    private String buildProductInterest(OmnichannelLeadRequest request) {
        List<String> parts = new ArrayList<>();
        if (hasText(request.getOccasion())) {
            parts.add("occasion=" + request.getOccasion().trim());
        }
        if (hasText(request.getBudget())) {
            parts.add("budget=" + request.getBudget().trim());
        }
        if (hasText(request.getLanguage())) {
            parts.add("language=" + request.getLanguage().trim());
        }
        return parts.isEmpty() ? null : String.join("; ", parts);
    }

    private int resolveLimit(Integer requestedLimit) {
        int max = Math.max(1, omnichannelProperties.getMaxProductCards());
        if (requestedLimit == null) {
            return max;
        }
        return Math.min(Math.max(requestedLimit, 1), max);
    }

    private String resolveEventType(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            return firstNonBlank(root.path("object").asText(null), "META_WEBHOOK");
        } catch (Exception ignored) {
            return "META_WEBHOOK";
        }
    }

    private String resolveExternalEventId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            return firstNonBlank(
                    root.path("entry").path(0).path("id").asText(null),
                    root.path("entry").path(0).path("time").asText(null)
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private Boolean validateWebhookSignature(String payload, String signature) {
        String secret = trimToNull(omnichannelProperties.getWebhookSecret());
        String receivedSignature = trimToNull(signature);
        if (secret == null || receivedSignature == null) {
            return null;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expectedSignature = "sha256=" + toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    receivedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception ignored) {
            return false;
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    private List<String> words(String value) {
        String normalized = normalizeText(value);
        if (!hasText(normalized)) {
            return List.of();
        }
        return java.util.Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(word -> word.length() >= 3)
                .toList();
    }

    private String baseUrl() {
        String url = trimToNull(omnichannelProperties.getWebsiteBaseUrl());
        if (url == null) {
            return "https://kpskrishnai.com";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String normalizeChannel(String channel) {
        String normalized = trimToNull(channel);
        return normalized == null ? "WHATSAPP" : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private String normalizeMobile(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return "+91 " + digits;
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            return "+91 " + digits.substring(2);
        }
        return trimToNull(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
