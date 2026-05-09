package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.MarketingProperties;
import com.retailshop.dto.OmnichannelLeadRequest;
import com.retailshop.dto.OmnichannelLeadResponse;
import com.retailshop.dto.OmnichannelProductCardResponse;
import com.retailshop.dto.OmnichannelProductSearchRequest;
import com.retailshop.dto.OmnichannelProductSearchResponse;
import com.retailshop.dto.WhatsAppBotWebhookResponse;
import com.retailshop.entity.OmnichannelConversation;
import com.retailshop.entity.OmnichannelConversationMessage;
import com.retailshop.entity.OmnichannelLead;
import com.retailshop.entity.Product;
import com.retailshop.repository.OmnichannelConversationMessageRepository;
import com.retailshop.repository.OmnichannelConversationRepository;
import com.retailshop.repository.OmnichannelLeadRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.OmnichannelCommerceService;
import com.retailshop.service.WhatsAppSalesBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class WhatsAppSalesBotServiceImpl implements WhatsAppSalesBotService {

    private static final String GUPSHUP_MESSAGE_ENDPOINT = "https://api.gupshup.io/wa/api/v1/msg";
    private static final Pattern MONEY_PATTERN = Pattern.compile("(?:₹|rs\\.?|inr)?\\s*(\\d{2,7})(?:\\s*(?:-|to|and|ते|पासून)\\s*(?:₹|rs\\.?|inr)?\\s*(\\d{2,7}))?", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP_WORDS = Set.of(
            "show", "send", "share", "list", "want", "need", "under", "below", "less", "than", "price", "budget",
            "items", "item", "products", "product", "collection", "please", "pls", "mala", "mujhe", "dikhao",
            "दाखवा", "पाहा", "खाली", "मध्ये", "आहे", "का", "साठी", "मला", "द्या"
    );

    private final MarketingProperties marketingProperties;
    private final OmnichannelCommerceService omnichannelCommerceService;
    private final ProductRepository productRepository;
    private final OmnichannelLeadRepository leadRepository;
    private final OmnichannelConversationRepository conversationRepository;
    private final OmnichannelConversationMessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WhatsAppSalesBotServiceImpl(MarketingProperties marketingProperties,
                                       OmnichannelCommerceService omnichannelCommerceService,
                                       ProductRepository productRepository,
                                       OmnichannelLeadRepository leadRepository,
                                       OmnichannelConversationRepository conversationRepository,
                                       OmnichannelConversationMessageRepository messageRepository,
                                       ObjectMapper objectMapper) {
        this(marketingProperties, omnichannelCommerceService, productRepository, leadRepository, conversationRepository, messageRepository, objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
    }

    WhatsAppSalesBotServiceImpl(MarketingProperties marketingProperties,
                                OmnichannelCommerceService omnichannelCommerceService,
                                ProductRepository productRepository,
                                OmnichannelLeadRepository leadRepository,
                                OmnichannelConversationRepository conversationRepository,
                                OmnichannelConversationMessageRepository messageRepository,
                                ObjectMapper objectMapper,
                                HttpClient httpClient) {
        this.marketingProperties = marketingProperties;
        this.omnichannelCommerceService = omnichannelCommerceService;
        this.productRepository = productRepository;
        this.leadRepository = leadRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public WhatsAppBotWebhookResponse handleWebhook(String payload, String signature) {
        Optional<InboundMessage> inbound = extractInboundMessage(payload);
        if (inbound.isEmpty()) {
            return WhatsAppBotWebhookResponse.builder()
                    .accepted(true)
                    .sent(false)
                    .message("Ignored webhook without a customer text message")
                    .build();
        }

        InboundMessage message = inbound.get();
        OmnichannelLeadResponse lead = captureLead(message, payload);
        BotUnderstanding understanding = understandMessage(message.text());
        BotReply reply = buildReply(understanding, lead);
        SendResult sendResult = sendWhatsAppText(message.from(), reply.text());
        saveOutboundMessage(lead.getLeadId(), reply.text(), sendResult);

        return WhatsAppBotWebhookResponse.builder()
                .accepted(true)
                .sent(sendResult.success())
                .message(sendResult.success() ? "Bot reply sent" : "Bot reply generated but sender is not configured or failed")
                .leadId(lead.getLeadId())
                .customerPhone(message.from())
                .replyText(reply.text())
                .productCount(reply.productCount())
                .providerMessageId(sendResult.messageId())
                .errorMessage(sendResult.errorMessage())
                .build();
    }

    private OmnichannelLeadResponse captureLead(InboundMessage message, String rawPayload) {
        OmnichannelLeadRequest request = new OmnichannelLeadRequest();
        request.setChannel("WHATSAPP");
        request.setExternalUserId(message.from());
        request.setCustomerHandleOrPhone(message.from());
        request.setMobile(message.from());
        request.setCustomerName(message.name());
        request.setExternalThreadId(message.messageId());
        request.setSourceMessageId(message.messageId());
        request.setSourceCampaign("whatsapp-sales-bot");
        request.setCampaignName("whatsapp-sales-bot");
        request.setMessageText(message.text());
        request.setQuery(message.text());
        request.setRawPayload(rawPayload);
        return omnichannelCommerceService.captureLead(request);
    }

    private BotReply buildReply(BotUnderstanding understanding, OmnichannelLeadResponse lead) {
        if (understanding.intent() == BotIntent.GREETING) {
            return new BotReply(buildGreetingReply(), 0);
        }
        if (understanding.intent() == BotIntent.ORDER_SUPPORT) {
            return new BotReply("Sure, I can help with your order. Please share your order number or registered mobile number.", 0);
        }
        if (understanding.intent() == BotIntent.HUMAN_HANDOFF) {
            return new BotReply("No problem. I will ask our team to help you personally. Please share your requirement or preferred call time.", 0);
        }
        if (understanding.intent() == BotIntent.THANKS) {
            return new BotReply("Most welcome. Tell me your budget or category whenever you want to see more options.", 0);
        }

        OmnichannelProductSearchRequest searchRequest = new OmnichannelProductSearchRequest();
        searchRequest.setLeadId(lead.getLeadId());
        searchRequest.setChannel("WHATSAPP");
        searchRequest.setSource("whatsapp");
        searchRequest.setCampaign("whatsapp-sales-bot");
        searchRequest.setQuery(understanding.searchText());
        searchRequest.setCategory(understanding.category());
        searchRequest.setOccasion(understanding.occasion());
        searchRequest.setMinPrice(understanding.minPrice());
        searchRequest.setMaxPrice(understanding.maxPrice());
        searchRequest.setInStockOnly(true);
        searchRequest.setLimit(5);

        OmnichannelProductSearchResponse products = omnichannelCommerceService.searchProducts(searchRequest);
        return new BotReply(formatProductReply(products, understanding), products.getProducts() == null ? 0 : products.getProducts().size());
    }

    private String buildGreetingReply() {
        List<String> categories = availableCategories().stream().limit(6).toList();
        String categoryText = categories.isEmpty() ? "jewellery, cosmetics, gifts" : String.join(", ", categories);
        return "Namaste! I can help you like a shop salesperson.\n"
                + "You can ask:\n"
                + "- Show earrings under 2000\n"
                + "- Bridal jewellery\n"
                + "- Cosmetics for gifting\n\n"
                + "Available categories: " + categoryText;
    }

    private String formatProductReply(OmnichannelProductSearchResponse response, BotUnderstanding understanding) {
        List<OmnichannelProductCardResponse> products = response.getProducts() == null ? List.of() : response.getProducts();
        if (products.isEmpty()) {
            return "I could not find an exact match for that. Please share category and budget, for example: \"earrings under 2000\" or \"cosmetics gift under 1000\".";
        }

        StringBuilder reply = new StringBuilder();
        reply.append("I found ").append(products.size()).append(" option");
        if (products.size() != 1) {
            reply.append("s");
        }
        if (hasText(understanding.category()) || understanding.maxPrice() != null || understanding.minPrice() != null) {
            reply.append(" for your requirement");
        }
        reply.append(":\n\n");

        int index = 1;
        for (OmnichannelProductCardResponse product : products) {
            reply.append(index++).append(". ").append(product.getName()).append("\n")
                    .append(formatPrice(product.getPrice())).append(" | ").append(defaultString(product.getStockLabel(), "Available")).append("\n");
            if (hasText(product.getShortBenefit())) {
                reply.append(product.getShortBenefit()).append("\n");
            }
            reply.append("Buy Now: ").append(defaultString(product.getBuyNowUrl(), product.getProductUrl())).append("\n\n");
        }
        reply.append("Want more options? Send category + budget, like \"necklace under 1500\".");
        return reply.toString().trim();
    }

    private BotUnderstanding understandMessage(String text) {
        BotUnderstanding ai = understandWithAi(text);
        if (ai != null && ai.intent() != BotIntent.UNKNOWN) {
            return ai;
        }
        return understandWithRules(text);
    }

    private BotUnderstanding understandWithAi(String text) {
        MarketingProperties.Ai ai = marketingProperties.getAi();
        if (ai == null || !ai.isEnabled() || !hasText(ai.getApiKey())) {
            return null;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", defaultString(ai.getModel(), "gpt-4.1-mini"));
            payload.put("temperature", 0.1);
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", """
                            You classify WhatsApp sales queries for an Indian retail shop selling jewellery, cosmetics, and gifts.
                            Return strict JSON only:
                            {"intent":"PRODUCT_SEARCH|GREETING|ORDER_SUPPORT|HUMAN_HANDOFF|THANKS|UNKNOWN","category":string|null,"searchText":string,"minPrice":number|null,"maxPrice":number|null,"occasion":string|null}
                            Extract category and price from English, Hindi, Hinglish, or Marathi.
                            Examples:
                            "show earrings under 2000" -> PRODUCT_SEARCH, category earrings, maxPrice 2000
                            "bridal set" -> PRODUCT_SEARCH, occasion wedding
                            "order status" -> ORDER_SUPPORT
                            """),
                    Map.of("role", "user", "content", text)
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("authorization", "Bearer " + ai.getApiKey().trim())
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return null;
            }
            String content = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("");
            JsonNode root = objectMapper.readTree(content);
            return new BotUnderstanding(
                    parseIntent(root.path("intent").asText("UNKNOWN")),
                    trimToNull(root.path("category").asText(null)),
                    defaultString(trimToNull(root.path("searchText").asText(null)), text),
                    numberOrNull(root.path("minPrice")),
                    numberOrNull(root.path("maxPrice")),
                    trimToNull(root.path("occasion").asText(null))
            );
        } catch (Exception exception) {
            log.debug("WhatsApp bot AI understanding failed; using rule fallback", exception);
            return null;
        }
    }

    private BotUnderstanding understandWithRules(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return new BotUnderstanding(BotIntent.UNKNOWN, null, "", null, null, null);
        }
        if (isGreeting(normalized)) {
            return new BotUnderstanding(BotIntent.GREETING, null, text, null, null, null);
        }
        if (containsAny(normalized, "thanks", "thank you", "धन्यवाद", "shukriya")) {
            return new BotUnderstanding(BotIntent.THANKS, null, text, null, null, null);
        }
        if (containsAny(normalized, "order", "delivery", "tracking", "status", "refund", "return", "ऑर्डर")) {
            return new BotUnderstanding(BotIntent.ORDER_SUPPORT, null, text, null, null, null);
        }
        if (containsAny(normalized, "human", "person", "call", "staff", "salesperson", "owner")) {
            return new BotUnderstanding(BotIntent.HUMAN_HANDOFF, null, text, null, null, null);
        }

        String category = detectCategory(normalized);
        PriceRange priceRange = extractPriceRange(normalized);
        String occasion = detectOccasion(normalized);
        String searchText = buildSearchText(text, category, occasion);
        return new BotUnderstanding(BotIntent.PRODUCT_SEARCH, category, searchText, priceRange.min(), priceRange.max(), occasion);
    }

    private Optional<InboundMessage> extractInboundMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            InboundMessage direct = extractDirectPayload(root);
            if (direct != null) {
                return Optional.of(direct);
            }
            InboundMessage meta = extractMetaPayload(root);
            if (meta != null) {
                return Optional.of(meta);
            }
            InboundMessage gupshup = extractGupshupPayload(root);
            if (gupshup != null) {
                return Optional.of(gupshup);
            }
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Unable to parse WhatsApp bot webhook payload", exception);
            return Optional.empty();
        }
    }

    private InboundMessage extractDirectPayload(JsonNode root) {
        String text = firstNonBlank(
                root.path("text").asText(null),
                root.path("message").path("text").asText(null),
                root.path("messageText").asText(null),
                root.path("body").asText(null)
        );
        String from = firstNonBlank(root.path("from").asText(null), root.path("mobile").asText(null), root.path("phone").asText(null));
        if (!hasText(text) || !hasText(from)) {
            return null;
        }
        return new InboundMessage(from, firstNonBlank(root.path("name").asText(null), root.path("customerName").asText(null), "Customer"), text, root.path("messageId").asText(null));
    }

    private InboundMessage extractMetaPayload(JsonNode root) {
        JsonNode value = root.path("entry").path(0).path("changes").path(0).path("value");
        JsonNode message = value.path("messages").path(0);
        if (message.isMissingNode() || message.isNull()) {
            return null;
        }
        String text = firstNonBlank(
                message.path("text").path("body").asText(null),
                message.path("interactive").path("button_reply").path("title").asText(null),
                message.path("interactive").path("list_reply").path("title").asText(null),
                message.path("button").path("text").asText(null)
        );
        String from = message.path("from").asText(null);
        if (!hasText(text) || !hasText(from)) {
            return null;
        }
        String name = firstNonBlank(value.path("contacts").path(0).path("profile").path("name").asText(null), "Customer");
        return new InboundMessage(from, name, text, message.path("id").asText(null));
    }

    private InboundMessage extractGupshupPayload(JsonNode root) {
        JsonNode payload = root.path("payload");
        JsonNode messagePayload = payload.path("payload");
        String text = firstNonBlank(
                messagePayload.path("text").asText(null),
                payload.path("text").asText(null),
                root.path("text").asText(null)
        );
        String from = firstNonBlank(
                payload.path("source").asText(null),
                payload.path("sender").path("phone").asText(null),
                root.path("source").asText(null),
                root.path("sender").path("phone").asText(null)
        );
        if (!hasText(text) || !hasText(from)) {
            return null;
        }
        String name = firstNonBlank(payload.path("sender").path("name").asText(null), root.path("sender").path("name").asText(null), "Customer");
        String id = firstNonBlank(payload.path("id").asText(null), messagePayload.path("id").asText(null), root.path("messageId").asText(null));
        return new InboundMessage(from, name, text, id);
    }

    private SendResult sendWhatsAppText(String to, String text) {
        String provider = marketingProperties.getWhatsapp().getProvider() == null
                ? "GUPSHUP"
                : marketingProperties.getWhatsapp().getProvider().trim().toUpperCase(Locale.ROOT);
        try {
            return switch (provider) {
                case "GUPSHUP" -> sendViaGupshup(to, text);
                case "META", "CLOUD_API", "WHATSAPP_CLOUD" -> sendViaMeta(to, text);
                case "TWILIO" -> sendViaTwilio(to, text);
                default -> new SendResult(false, null, "Unsupported WhatsApp bot provider: " + provider);
            };
        } catch (Exception exception) {
            log.warn("WhatsApp bot send failed for provider {}", provider, exception);
            return new SendResult(false, null, "Unable to send WhatsApp bot reply");
        }
    }

    private SendResult sendViaGupshup(String to, String text) throws IOException, InterruptedException {
        MarketingProperties.Gupshup gupshup = marketingProperties.getGupshup();
        if (!hasText(gupshup.getApiKey()) || !hasText(gupshup.getAppName()) || !hasText(gupshup.getSourceNumber())) {
            return new SendResult(false, null, "Gupshup bot sender needs API key, app name, and source number");
        }
        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("channel", "whatsapp");
        formData.put("source", digitsOnly(gupshup.getSourceNumber()));
        formData.put("destination", formatIndianDestination(to));
        formData.put("src.name", gupshup.getAppName().trim());
        formData.put("message", objectMapper.writeValueAsString(Map.of("type", "text", "text", text)));

        HttpRequest request = HttpRequest.newBuilder(URI.create(GUPSHUP_MESSAGE_ENDPOINT))
                .POST(HttpRequest.BodyPublishers.ofString(toFormBody(formData)))
                .header("apikey", gupshup.getApiKey().trim())
                .header("content-type", "application/x-www-form-urlencoded")
                .header("accept", "application/json")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = parseJson(response.body());
        if (response.statusCode() >= 400) {
            return new SendResult(false, null, extractError(root, "Gupshup bot reply failed"));
        }
        String messageId = firstNonBlank(root.path("messageId").asText(null), root.path("messageID").asText(null), root.path("id").asText(null));
        String status = root.path("status").asText("");
        return new SendResult(!messageId.isBlank() || "submitted".equalsIgnoreCase(status), messageId, null);
    }

    private SendResult sendViaMeta(String to, String text) throws IOException, InterruptedException {
        String accessToken = marketingProperties.getMeta().getAccessToken();
        String phoneNumberId = marketingProperties.getWhatsapp().getPhoneNumberId();
        if (!hasText(accessToken) || !hasText(phoneNumberId)) {
            return new SendResult(false, null, "Meta WhatsApp bot sender needs access token and phone number id");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", formatIndianDestination(to));
        body.put("type", "text");
        body.put("text", Map.of("preview_url", true, "body", text));

        String version = defaultString(marketingProperties.getMeta().getGraphVersion(), "v23.0");
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/" + version + "/" + phoneNumberId.trim() + "/messages"))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .header("authorization", "Bearer " + accessToken.trim())
                .header("content-type", "application/json")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = parseJson(response.body());
        if (response.statusCode() >= 400) {
            return new SendResult(false, null, extractError(root, "Meta WhatsApp bot reply failed"));
        }
        return new SendResult(true, root.path("messages").path(0).path("id").asText(""), null);
    }

    private SendResult sendViaTwilio(String to, String text) throws IOException, InterruptedException {
        MarketingProperties.Twilio twilio = marketingProperties.getTwilio();
        if (!hasText(twilio.getAccountSid()) || !hasText(twilio.getAuthToken()) || !hasText(twilio.getWhatsappFrom())) {
            return new SendResult(false, null, "Twilio WhatsApp bot sender is not configured");
        }
        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("From", formatTwilioWhatsAppAddress(twilio.getWhatsappFrom()));
        formData.put("To", formatTwilioWhatsAppAddress(to));
        formData.put("Body", text);

        String auth = java.util.Base64.getEncoder().encodeToString((twilio.getAccountSid().trim() + ":" + twilio.getAuthToken().trim()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + twilio.getAccountSid().trim() + "/Messages.json"))
                .POST(HttpRequest.BodyPublishers.ofString(toFormBody(formData)))
                .header("authorization", "Basic " + auth)
                .header("content-type", "application/x-www-form-urlencoded")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = parseJson(response.body());
        if (response.statusCode() >= 400) {
            return new SendResult(false, null, extractError(root, "Twilio WhatsApp bot reply failed"));
        }
        return new SendResult(true, root.path("sid").asText(""), null);
    }

    private void saveOutboundMessage(java.util.UUID leadId, String replyText, SendResult sendResult) {
        if (leadId == null || !hasText(replyText)) {
            return;
        }
        try {
            Optional<OmnichannelLead> lead = leadRepository.findById(leadId);
            if (lead.isEmpty()) {
                return;
            }
            Optional<OmnichannelConversation> conversation = conversationRepository.findFirstByLead_IdAndChannelOrderByUpdatedAtDesc(leadId, "WHATSAPP");
            if (conversation.isEmpty()) {
                return;
            }
            OmnichannelConversationMessage message = new OmnichannelConversationMessage();
            message.setConversation(conversation.get());
            message.setDirection("OUTBOUND");
            message.setMessageType("TEXT");
            message.setMessageText(replyText);
            message.setRawPayload("sent=" + sendResult.success() + ";messageId=" + safe(sendResult.messageId()) + ";error=" + safe(sendResult.errorMessage()));
            messageRepository.save(message);
        } catch (Exception exception) {
            log.debug("Unable to save WhatsApp bot outbound message", exception);
        }
    }

    private String detectCategory(String normalizedText) {
        for (String category : availableCategories()) {
            String normalizedCategory = normalize(category);
            if (normalizedText.contains(normalizedCategory)) {
                return category;
            }
        }
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("earring", "earrings"),
                Map.entry("earrings", "earrings"),
                Map.entry("झुमका", "earrings"),
                Map.entry("necklace", "necklace"),
                Map.entry("हार", "necklace"),
                Map.entry("bangle", "bangles"),
                Map.entry("bangles", "bangles"),
                Map.entry("बांगडी", "bangles"),
                Map.entry("cosmetic", "cosmetics"),
                Map.entry("cosmetics", "cosmetics"),
                Map.entry("makeup", "cosmetics"),
                Map.entry("gift", "gifts"),
                Map.entry("bridal", "bridal"),
                Map.entry("mangalsutra", "mangalsutra"),
                Map.entry("ring", "rings")
        );
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            if (normalizedText.contains(alias.getKey())) {
                return alias.getValue();
            }
        }
        return null;
    }

    private PriceRange extractPriceRange(String normalizedText) {
        Matcher matcher = MONEY_PATTERN.matcher(normalizedText);
        BigDecimal first = null;
        BigDecimal second = null;
        while (matcher.find()) {
            BigDecimal value = new BigDecimal(matcher.group(1));
            if (first == null) {
                first = value;
            }
            if (matcher.group(2) != null) {
                second = new BigDecimal(matcher.group(2));
                break;
            }
        }
        if (first == null) {
            return new PriceRange(null, null);
        }
        if (second != null) {
            return new PriceRange(first.min(second), first.max(second));
        }
        if (containsAny(normalizedText, "under", "below", "less than", "within", "upto", "up to", "खाली", "कमी", "andar")) {
            return new PriceRange(null, first);
        }
        if (containsAny(normalizedText, "above", "more than", "over", "जास्त")) {
            return new PriceRange(first, null);
        }
        return new PriceRange(null, first);
    }

    private String detectOccasion(String normalizedText) {
        if (containsAny(normalizedText, "wedding", "bridal", "लग्न", "shaadi")) {
            return "wedding";
        }
        if (containsAny(normalizedText, "gift", "birthday", "anniversary", "भेट")) {
            return "gifting";
        }
        if (containsAny(normalizedText, "party", "function", "festival", "सण")) {
            return "occasion wear";
        }
        return null;
    }

    private String buildSearchText(String original, String category, String occasion) {
        List<String> terms = new ArrayList<>();
        if (hasText(category)) {
            terms.add(category);
        }
        if (hasText(occasion)) {
            terms.add(occasion);
        }
        for (String token : normalize(original).split("\\s+")) {
            if (token.length() > 2 && !STOP_WORDS.contains(token) && terms.stream().noneMatch(token::equalsIgnoreCase)) {
                terms.add(token);
            }
        }
        return terms.isEmpty() ? original : String.join(" ", terms);
    }

    private List<String> availableCategories() {
        LinkedHashSet<String> categories = productRepository.findAll().stream()
                .map(Product::getCategory)
                .filter(this::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<>(categories);
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

    private boolean isGreeting(String normalizedText) {
        if (containsAny(normalizedText, "hello", "namaste", "नमस्ते", "नमस्कार")) {
            return true;
        }
        for (String token : normalizedText.split("\\s+")) {
            if ("hi".equals(token) || "hey".equals(token)) {
                return true;
            }
        }
        return false;
    }

    private BotIntent parseIntent(String raw) {
        try {
            return BotIntent.valueOf(defaultString(raw, "UNKNOWN").trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return BotIntent.UNKNOWN;
        }
    }

    private BigDecimal numberOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.decimalValue();
    }

    private JsonNode parseJson(String value) throws IOException {
        if (!hasText(value)) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(value);
    }

    private String extractError(JsonNode root, String fallback) {
        String error = firstNonBlank(
                root.path("error").path("message").asText(null),
                root.path("message").asText(null),
                root.path("details").asText(null)
        );
        return hasText(error) ? error : fallback;
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "Price available on request";
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setMaximumFractionDigits(0);
        return format.format(price);
    }

    private String toFormBody(Map<String, String> formData) {
        return formData.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String formatIndianDestination(String value) {
        String digits = digitsOnly(value);
        if (digits.length() == 10) {
            return "91" + digits;
        }
        return digits;
    }

    private String formatTwilioWhatsAppAddress(String value) {
        String trimmed = defaultString(value, "");
        if (trimmed.startsWith("whatsapp:")) {
            return trimmed;
        }
        String digits = formatIndianDestination(trimmed);
        return "whatsapp:+" + digits;
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
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

    private record InboundMessage(String from, String name, String text, String messageId) {
    }

    private record BotUnderstanding(BotIntent intent, String category, String searchText, BigDecimal minPrice, BigDecimal maxPrice, String occasion) {
    }

    private record BotReply(String text, int productCount) {
    }

    private record PriceRange(BigDecimal min, BigDecimal max) {
    }

    private record SendResult(boolean success, String messageId, String errorMessage) {
    }

    private enum BotIntent {
        PRODUCT_SEARCH,
        GREETING,
        ORDER_SUPPORT,
        HUMAN_HANDOFF,
        THANKS,
        UNKNOWN
    }
}
