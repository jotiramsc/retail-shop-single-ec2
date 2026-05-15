package com.retailshop.service.bot.impl;

import com.retailshop.dto.bot.BotContext;
import com.retailshop.dto.bot.BotIntentClassification;
import com.retailshop.dto.bot.BotMemoryRecord;
import com.retailshop.enums.WhatsAppBotIntent;
import com.retailshop.service.bot.BotIntentService;
import com.retailshop.service.bot.BotOpenAiService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BotIntentServiceImpl implements BotIntentService {

    private static final Pattern MONEY_PATTERN = Pattern.compile("(?:₹|rs\\.?|inr)?\\s*(\\d{2,7})(?:\\s*(?:-|to|and)\\s*(?:₹|rs\\.?|inr)?\\s*(\\d{2,7}))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_PATTERN = Pattern.compile("\\b(?:KPS\\d+|ORD[-A-Z0-9]+)\\b", Pattern.CASE_INSENSITIVE);

    private final ObjectProvider<BotOpenAiService> openAiServiceProvider;

    public BotIntentServiceImpl(ObjectProvider<BotOpenAiService> openAiServiceProvider) {
        this.openAiServiceProvider = openAiServiceProvider;
    }

    @Override
    public BotIntentClassification classify(String message, BotContext context, List<BotMemoryRecord> memories) {
        BotIntentClassification rule = classifyWithRules(message, context);
        BotOpenAiService openAiService = openAiServiceProvider.getIfAvailable();
        Optional<BotIntentClassification> ai = openAiService == null
                ? Optional.empty()
                : openAiService.classifyIntent(message, context, memories);
        if (ai.isPresent() && ai.get().getConfidence() >= 0.65) {
            return merge(rule, ai.get());
        }
        return rule;
    }

    @Override
    public Optional<String> polishReply(String message,
                                        BotIntentClassification intent,
                                        BotContext context,
                                        List<BotMemoryRecord> memories,
                                        String factualDraft) {
        BotOpenAiService openAiService = openAiServiceProvider.getIfAvailable();
        if (openAiService == null) {
            return Optional.empty();
        }
        return openAiService.generateReply(message, intent, context, memories, factualDraft);
    }

    private BotIntentClassification merge(BotIntentClassification rule, BotIntentClassification ai) {
        return ai.toBuilder()
                .category(firstNonBlank(ai.getCategory(), rule.getCategory()))
                .searchText(firstNonBlank(ai.getSearchText(), rule.getSearchText()))
                .minPrice(ai.getMinPrice() == null ? rule.getMinPrice() : ai.getMinPrice())
                .maxPrice(ai.getMaxPrice() == null ? rule.getMaxPrice() : ai.getMaxPrice())
                .occasion(firstNonBlank(ai.getOccasion(), rule.getOccasion()))
                .orderNumber(firstNonBlank(ai.getOrderNumber(), rule.getOrderNumber()))
                .build();
    }

    private BotIntentClassification classifyWithRules(String message, BotContext context) {
        String normalized = normalize(message);
        PriceRange price = extractPriceRange(normalized);
        String category = detectCategory(normalized, context);
        String occasion = detectOccasion(normalized);
        String orderNumber = extractOrderNumber(message);

        if (normalized.isBlank()) {
            return BotIntentClassification.fallback(message);
        }
        if (isGreeting(normalized) || isMenuRequest(normalized)) {
            return base(WhatsAppBotIntent.WELCOME_MENU, message, 0.9).build();
        }
        if (containsAny(normalized, "category", "categories", "collection", "browse", "कॅटेगरी")) {
            return base(WhatsAppBotIntent.BROWSE_CATEGORIES, message, 0.9).category(category).build();
        }
        if (containsAny(normalized, "human", "agent", "support", "complaint", "call", "staff", "issue", "problem")) {
            return base(WhatsAppBotIntent.AGENT_HANDOFF, message, 0.9).build();
        }
        if (containsAny(normalized, "coupon", "offer", "discount", "deal", "सूट")) {
            return base(WhatsAppBotIntent.OFFERS_AND_COUPONS, message, 0.86).category(category).build();
        }
        if (containsAny(normalized, "payment", "paid", "unpaid", "transaction", "razorpay", "pay", "पेमेंट")) {
            return base(WhatsAppBotIntent.PAYMENT_STATUS, message, 0.86).orderNumber(orderNumber).build();
        }
        if (containsAny(normalized, "refund", "refunded", "परतावा")) {
            return base(WhatsAppBotIntent.REFUND_STATUS, message, 0.86).orderNumber(orderNumber).build();
        }
        if (containsAny(normalized, "delivery", "track", "tracking", "shipped", "dispatch", "where is", "status")) {
            return base(WhatsAppBotIntent.DELIVERY_STATUS, message, 0.88).orderNumber(orderNumber).build();
        }
        if (containsAny(normalized, "how much", "spent", "total value", "total order value", "total spent")) {
            return base(WhatsAppBotIntent.TOTAL_ORDER_VALUE, message, 0.88).build();
        }
        if (containsAny(normalized, "how many", "order count", "count orders")) {
            return base(WhatsAppBotIntent.ORDER_COUNT, message, 0.88).build();
        }
        if (containsAny(normalized, "latest order", "last order", "recent order")) {
            return base(WhatsAppBotIntent.LATEST_ORDER, message, 0.88).orderNumber(orderNumber).build();
        }
        if (containsAny(normalized, "my orders", "order history", "previous orders", "bills", "invoice")) {
            return base(WhatsAppBotIntent.ORDER_HISTORY, message, 0.88).orderNumber(orderNumber).build();
        }
        if (hasText(orderNumber) || containsAny(normalized, "order")) {
            return base(WhatsAppBotIntent.ORDER_DETAILS, message, 0.82).orderNumber(orderNumber).build();
        }
        if (containsAny(normalized, "reorder", "buy again", "repeat previous")) {
            return base(WhatsAppBotIntent.REORDER, message, 0.88).build();
        }
        if (containsAny(normalized, "login", "otp", "profile", "account", "address")) {
            return base(WhatsAppBotIntent.ACCOUNT_HELP, message, 0.8).build();
        }
        if (containsAny(normalized, "cart", "checkout")) {
            return base(WhatsAppBotIntent.CART_CHECKOUT_HELP, message, 0.8).build();
        }
        if (hasText(category) || price.min() != null || price.max() != null || hasText(occasion)
                || containsAny(normalized, "show", "need", "want", "gift", "bridal", "daily wear", "trending", "new")) {
            return base(WhatsAppBotIntent.SEARCH_PRODUCTS, message, 0.78)
                    .category(category)
                    .minPrice(price.min())
                    .maxPrice(price.max())
                    .occasion(occasion)
                    .build();
        }
        return base(WhatsAppBotIntent.FALLBACK, message, 0.45)
                .needsClarification(true)
                .clarificationQuestion("Would you like to shop products, track an order, check payment, or talk to support?")
                .build();
    }

    private BotIntentClassification.BotIntentClassificationBuilder base(WhatsAppBotIntent intent, String message, double confidence) {
        return BotIntentClassification.builder()
                .intent(intent)
                .confidence(confidence)
                .searchText(message);
    }

    private PriceRange extractPriceRange(String text) {
        Matcher matcher = MONEY_PATTERN.matcher(text);
        BigDecimal first = null;
        BigDecimal second = null;
        while (matcher.find()) {
            first = new BigDecimal(matcher.group(1));
            if (matcher.group(2) != null) {
                second = new BigDecimal(matcher.group(2));
            }
            break;
        }
        if (first == null) {
            return new PriceRange(null, null);
        }
        if (second != null) {
            return new PriceRange(first.min(second), first.max(second));
        }
        if (containsAny(text, "above", "over", "more than")) {
            return new PriceRange(first, null);
        }
        return new PriceRange(null, first);
    }

    private String detectCategory(String text, BotContext context) {
        for (Map.Entry<String, String> entry : aliases().entrySet()) {
            if (text.contains(entry.getKey()) || isCloseWord(text, entry.getKey())) {
                return resolveCatalogCategory(entry.getValue(), context);
            }
        }
        if (context != null && context.getCategories() != null) {
            for (String category : context.getCategories()) {
                String normalized = normalize(category);
                if (hasText(normalized) && (text.contains(normalized) || isCloseWord(text, normalized))) {
                    return category;
                }
            }
        }
        return null;
    }

    private String resolveCatalogCategory(String alias, BotContext context) {
        if (context == null || context.getCategories() == null) {
            return alias;
        }
        String normalizedAlias = normalize(alias);
        for (String category : context.getCategories()) {
            String normalizedCategory = normalize(category);
            if (normalizedCategory.equals(normalizedAlias)
                    || normalizedCategory.contains(normalizedAlias)
                    || normalizedAlias.contains(normalizedCategory)
                    || isCloseWord(normalizedAlias, normalizedCategory)) {
                return category;
            }
        }
        return alias;
    }

    private Map<String, String> aliases() {
        return Map.ofEntries(
                Map.entry("necklace", "necklace"),
                Map.entry("neckalce", "necklace"),
                Map.entry("neckalace", "necklace"),
                Map.entry("neckless", "necklace"),
                Map.entry("neck piece", "necklace"),
                Map.entry("neckpiece", "necklace"),
                Map.entry("chain", "necklace"),
                Map.entry("mala", "necklace"),
                Map.entry("haar", "necklace"),
                Map.entry("set", "necklace"),
                Map.entry("हार", "necklace"),
                Map.entry("माळ", "necklace"),
                Map.entry("माला", "necklace"),
                Map.entry("earring", "earrings"),
                Map.entry("earrings", "earrings"),
                Map.entry("earings", "earrings"),
                Map.entry("earing", "earrings"),
                Map.entry("tops", "earrings"),
                Map.entry("stud", "earrings"),
                Map.entry("jhumka", "earrings"),
                Map.entry("झुमका", "earrings"),
                Map.entry("bangle", "bangles"),
                Map.entry("bangles", "bangles"),
                Map.entry("bangels", "bangles"),
                Map.entry("bangale", "bangles"),
                Map.entry("chudi", "bangles"),
                Map.entry("चुडी", "bangles"),
                Map.entry("बांगडी", "bangles"),
                Map.entry("bracelet", "bracelet"),
                Map.entry("ring", "rings"),
                Map.entry("rings", "rings"),
                Map.entry("anguthi", "rings"),
                Map.entry("mangalsutra", "mangalsutra"),
                Map.entry("nose pin", "nose pin"),
                Map.entry("nosepin", "nose pin"),
                Map.entry("nath", "nose pin"),
                Map.entry("pendant", "pendant"),
                Map.entry("cosmetic", "cosmetics"),
                Map.entry("cosmetics", "cosmetics"),
                Map.entry("makeup", "cosmetics"),
                Map.entry("lipstick", "cosmetics"),
                Map.entry("kajal", "cosmetics"),
                Map.entry("eyeliner", "cosmetics"),
                Map.entry("compact", "cosmetics"),
                Map.entry("foundation", "cosmetics"),
                Map.entry("gift", "gifts"),
                Map.entry("gifting", "gifts"),
                Map.entry("bridal", "bridal")
        );
    }

    private String detectOccasion(String text) {
        if (containsAny(text, "wedding", "bridal", "shaadi", "लग्न")) {
            return "wedding";
        }
        if (containsAny(text, "gift", "birthday", "anniversary", "भेट")) {
            return "gifting";
        }
        if (containsAny(text, "daily", "office", "regular")) {
            return "daily wear";
        }
        if (containsAny(text, "party", "festival", "function", "सण")) {
            return "occasion wear";
        }
        return null;
    }

    private String extractOrderNumber(String message) {
        Matcher matcher = ORDER_PATTERN.matcher(message == null ? "" : message);
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : null;
    }

    private boolean isGreeting(String value) {
        return value.equals("hi") || value.equals("hello") || value.equals("hey") || value.equals("namaste")
                || value.equals("नमस्कार") || value.equals("नमस्ते");
    }

    private boolean isMenuRequest(String value) {
        return value.equals("help") || value.equals("menu") || value.equals("products") || value.equals("shop")
                || value.equals("order") || value.equals("payment");
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

    private boolean isCloseWord(String text, String target) {
        for (String token : normalize(text).split("\\s+")) {
            if (levenshtein(token, normalize(target), 3) <= Math.min(3, Math.max(1, normalize(target).length() / 4))) {
                return true;
            }
        }
        return false;
    }

    private int levenshtein(String left, String right, int stopAfter) {
        if (!hasText(left) || !hasText(right)) {
            return stopAfter + 1;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int i = 0; i <= right.length(); i++) {
            previous[i] = i;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            int minimum = current[0];
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                minimum = Math.min(minimum, current[j]);
            }
            if (minimum > stopAfter) {
                return stopAfter + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").trim();
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record PriceRange(BigDecimal min, BigDecimal max) {
    }
}
