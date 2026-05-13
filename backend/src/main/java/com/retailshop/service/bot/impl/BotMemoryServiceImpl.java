package com.retailshop.service.bot.impl;

import com.retailshop.config.BotProperties;
import com.retailshop.dto.bot.BotContext;
import com.retailshop.dto.bot.BotMemoryRecord;
import com.retailshop.service.bot.BotMemoryService;
import com.retailshop.service.bot.BotOpenAiService;
import com.retailshop.service.bot.QdrantMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotMemoryServiceImpl implements BotMemoryService {

    private final BotProperties properties;
    private final BotOpenAiService openAiService;
    private final QdrantMemoryService qdrantMemoryService;

    @Override
    public List<BotMemoryRecord> retrieve(String mobile, String query, int limit) {
        if (!properties.isEnabled() || !properties.isMemoryEnabled() || !hasText(query)) {
            return List.of();
        }
        Optional<List<Double>> vector = openAiService.embed(query);
        return vector.map(values -> qdrantMemoryService.searchByMobile(mobile, values, limit)).orElse(List.of());
    }

    @Override
    public void rememberInteraction(String mobile, String inboundText, String outboundText, BotContext context) {
        if (!properties.isEnabled() || !properties.isMemoryEnabled() || !properties.isConversationSummaryEnabled()) {
            return;
        }
        try {
            String summary = openAiService.summarizeMemory(inboundText, outboundText, context)
                    .orElseGet(() -> fallbackSummary(inboundText, outboundText));
            if (!hasText(summary)) {
                return;
            }
            Optional<List<Double>> vector = openAiService.embed(summary);
            if (vector.isEmpty()) {
                return;
            }
            BotMemoryRecord memory = BotMemoryRecord.builder()
                    .customerId(context == null || context.getCustomer() == null ? null : context.getCustomer().getId().toString())
                    .mobile(normalizeMobile(mobile))
                    .memoryType(memoryType(inboundText, outboundText))
                    .createdAt(LocalDateTime.now())
                    .source("WHATSAPP")
                    .summaryText(summary)
                    .tags(tags(inboundText + " " + outboundText))
                    .build();
            qdrantMemoryService.upsert(UUID.randomUUID().toString(), vector.get(), memory);
        } catch (Exception exception) {
            log.debug("Bot memory write failed", exception);
        }
    }

    private String fallbackSummary(String inboundText, String outboundText) {
        String combined = normalize(inboundText + " " + outboundText);
        if (containsAny(combined, "necklace", "earring", "bangle", "cosmetic", "gift", "bridal", "order", "payment", "refund", "delivery")) {
            String trimmed = (safe(inboundText) + " -> " + safe(outboundText)).replaceAll("\\s+", " ").trim();
            return trimmed.length() > 240 ? trimmed.substring(0, 237) + "..." : trimmed;
        }
        return "";
    }

    private String memoryType(String inboundText, String outboundText) {
        String text = normalize(inboundText + " " + outboundText);
        if (containsAny(text, "payment", "refund", "failed", "transaction")) {
            return "PAYMENT_HISTORY_CONTEXT";
        }
        if (containsAny(text, "delivery", "track", "shipped", "delayed")) {
            return "DELIVERY_ISSUE_CONTEXT";
        }
        if (containsAny(text, "order", "reorder", "invoice")) {
            return "PURCHASE_PATTERN";
        }
        if (containsAny(text, "gift", "birthday", "wedding", "bridal", "festival")) {
            return "OCCASION_NEED";
        }
        if (containsAny(text, "support", "agent", "issue", "problem", "complaint")) {
            return "SUPPORT_ISSUE";
        }
        return "PRODUCT_INTEREST";
    }

    private List<String> tags(String value) {
        String text = normalize(value);
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addIfPresent(tags, text, "necklace");
        addIfPresent(tags, text, "earrings");
        addIfPresent(tags, text, "bangles");
        addIfPresent(tags, text, "cosmetics");
        addIfPresent(tags, text, "gift");
        addIfPresent(tags, text, "bridal");
        addIfPresent(tags, text, "order");
        addIfPresent(tags, text, "payment");
        addIfPresent(tags, text, "delivery");
        addIfPresent(tags, text, "refund");
        return tags.stream().toList();
    }

    private void addIfPresent(LinkedHashSet<String> tags, String text, String tag) {
        if (text.contains(tag)) {
            tags.add(tag);
        }
    }

    private boolean containsAny(String value, String... needles) {
        if (!hasText(value)) {
            return false;
        }
        for (String needle : needles) {
            if (value.contains(normalize(needle))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeMobile(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        return digits.length() <= 10 ? digits : digits.substring(digits.length() - 10);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
