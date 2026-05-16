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
import com.retailshop.dto.bot.BotInboundMessage;
import com.retailshop.dto.bot.BotIntentClassification;
import com.retailshop.dto.whatsapp.WhatsAppInteractiveOption;
import com.retailshop.dto.whatsapp.WhatsAppInteractiveSection;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.Offer;
import com.retailshop.entity.OmnichannelConversation;
import com.retailshop.entity.OmnichannelConversationMessage;
import com.retailshop.entity.OmnichannelLead;
import com.retailshop.entity.Product;
import com.retailshop.entity.ProductCategoryOption;
import com.retailshop.entity.ReceiptSettings;
import com.retailshop.enums.WhatsAppBotIntent;
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.repository.OfferRepository;
import com.retailshop.repository.OmnichannelConversationMessageRepository;
import com.retailshop.repository.OmnichannelConversationRepository;
import com.retailshop.repository.OmnichannelLeadRepository;
import com.retailshop.repository.ProductCategoryOptionRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.repository.ReceiptSettingsRepository;
import com.retailshop.service.OmnichannelCommerceService;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.WhatsAppMessageService;
import com.retailshop.service.WhatsAppSalesBotService;
import com.retailshop.service.bot.BotOrchestratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class WhatsAppSalesBotServiceImpl implements WhatsAppSalesBotService {

    private static final Pattern MONEY_PATTERN = Pattern.compile("(?:₹|rs\\.?|inr)?\\s*(\\d{2,7})(?:\\s*(?:-|to|and|ते|पासून)\\s*(?:₹|rs\\.?|inr)?\\s*(\\d{2,7}))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("\\b(?:KPS\\d+|ORD[-A-Z0-9]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Duration INBOUND_DEDUP_TTL = Duration.ofMinutes(15);
    private static final Duration BOT_SESSION_TTL = Duration.ofMinutes(45);
    private static final int SHOWN_PRODUCT_HISTORY_CAP = 40;
    private static final int PRODUCT_IMAGE_SEND_LIMIT = 5;
    private static final Map<String, Instant> RECENT_INBOUND_MESSAGES = new ConcurrentHashMap<>();
    private static final Map<String, BotSession> BOT_SESSIONS = new ConcurrentHashMap<>();
    private static final Set<String> STOP_WORDS = Set.of(
            "show", "send", "share", "list", "want", "need", "under", "below", "less", "than", "price", "budget",
            "items", "item", "products", "product", "collection", "please", "pls", "mala", "mujhe", "dikhao",
            "dakhwa", "dakhav", "dakhava", "dakhavayche", "hava", "havi", "chahiye",
            "दाखवा", "पाहा", "खाली", "मध्ये", "आहे", "का", "साठी", "मला", "द्या"
    );
    private static final Set<String> COLOR_STYLE_TERMS = Set.of(
            "green", "red", "maroon", "black", "white", "gold", "golden", "silver", "pink", "blue",
            "emerald", "pearl", "kundan", "traditional", "modern", "lightweight", "heavy", "simple",
            "premium", "party", "daily", "office", "bridal", "wedding", "gift", "festive",
            "हिरवा", "लाल", "का��ा", "पांढरा", "सोनेरी", "चांदी", "मोती", "साधा", "लग्न", "भेट"
    );
    private static final Set<String> PRODUCT_CATEGORY_CONFLICT_GROUPS = Set.of(
            "earrings", "necklace", "bangles", "bracelet", "cosmetics", "mangalsutra", "nose pin", "pendant", "rings"
    );

    private final MarketingProperties marketingProperties;
    private final OmnichannelCommerceService omnichannelCommerceService;
    private final CustomerOrderRepository orderRepository;
    private final OfferRepository offerRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryOptionRepository productCategoryOptionRepository;
    private final OmnichannelLeadRepository leadRepository;
    private final OmnichannelConversationRepository conversationRepository;
    private final OmnichannelConversationMessageRepository messageRepository;
    private final WhatsAppMessageService whatsAppMessageService;
    private final ReceiptSettingsRepository receiptSettingsRepository;
    private final BotOrchestratorService botOrchestratorService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WhatsAppSalesBotServiceImpl(MarketingProperties marketingProperties,
                                       OmnichannelCommerceService omnichannelCommerceService,
                                       CustomerOrderRepository orderRepository,
                                       OfferRepository offerRepository,
                                       ProductRepository productRepository,
                                       OmnichannelLeadRepository leadRepository,
                                       OmnichannelConversationRepository conversationRepository,
                                       OmnichannelConversationMessageRepository messageRepository,
                                       WhatsAppMessageService whatsAppMessageService,
                                       ObjectMapper objectMapper) {
        this(marketingProperties, omnichannelCommerceService, orderRepository, offerRepository, productRepository, leadRepository, conversationRepository, messageRepository, whatsAppMessageService, objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(), null, null, null);
    }

    @Autowired
    public WhatsAppSalesBotServiceImpl(MarketingProperties marketingProperties,
                                       OmnichannelCommerceService omnichannelCommerceService,
                                       CustomerOrderRepository orderRepository,
                                       OfferRepository offerRepository,
                                       ProductRepository productRepository,
                                       OmnichannelLeadRepository leadRepository,
                                       OmnichannelConversationRepository conversationRepository,
                                       OmnichannelConversationMessageRepository messageRepository,
                                       WhatsAppMessageService whatsAppMessageService,
                                       ProductCategoryOptionRepository productCategoryOptionRepository,
                                       ReceiptSettingsRepository receiptSettingsRepository,
                                       ObjectMapper objectMapper,
                                       ObjectProvider<BotOrchestratorService> botOrchestratorProvider) {
        this(marketingProperties, omnichannelCommerceService, orderRepository, offerRepository, productRepository, leadRepository, conversationRepository, messageRepository, whatsAppMessageService, objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(), receiptSettingsRepository, botOrchestratorProvider.getIfAvailable(), productCategoryOptionRepository);
    }

    WhatsAppSalesBotServiceImpl(MarketingProperties marketingProperties,
                                OmnichannelCommerceService omnichannelCommerceService,
                                CustomerOrderRepository orderRepository,
                                OfferRepository offerRepository,
                                ProductRepository productRepository,
                                OmnichannelLeadRepository leadRepository,
                                OmnichannelConversationRepository conversationRepository,
                                OmnichannelConversationMessageRepository messageRepository,
                                WhatsAppMessageService whatsAppMessageService,
                                ObjectMapper objectMapper,
                                HttpClient httpClient) {
        this(marketingProperties, omnichannelCommerceService, orderRepository, offerRepository, productRepository, leadRepository, conversationRepository, messageRepository, whatsAppMessageService,
                objectMapper, httpClient, null, null, null);
    }

    WhatsAppSalesBotServiceImpl(MarketingProperties marketingProperties,
                                OmnichannelCommerceService omnichannelCommerceService,
                                CustomerOrderRepository orderRepository,
                                OfferRepository offerRepository,
                                ProductRepository productRepository,
                                OmnichannelLeadRepository leadRepository,
                                OmnichannelConversationRepository conversationRepository,
                                OmnichannelConversationMessageRepository messageRepository,
                                WhatsAppMessageService whatsAppMessageService,
                                ObjectMapper objectMapper,
                                HttpClient httpClient,
                                ReceiptSettingsRepository receiptSettingsRepository,
                                BotOrchestratorService botOrchestratorService) {
        this(marketingProperties, omnichannelCommerceService, orderRepository, offerRepository, productRepository, leadRepository, conversationRepository, messageRepository, whatsAppMessageService,
                objectMapper, httpClient, receiptSettingsRepository, botOrchestratorService, null);
    }

    WhatsAppSalesBotServiceImpl(MarketingProperties marketingProperties,
                                OmnichannelCommerceService omnichannelCommerceService,
                                CustomerOrderRepository orderRepository,
                                OfferRepository offerRepository,
                                ProductRepository productRepository,
                                OmnichannelLeadRepository leadRepository,
                                OmnichannelConversationRepository conversationRepository,
                                OmnichannelConversationMessageRepository messageRepository,
                                WhatsAppMessageService whatsAppMessageService,
                                ObjectMapper objectMapper,
                                HttpClient httpClient,
                                ReceiptSettingsRepository receiptSettingsRepository,
                                BotOrchestratorService botOrchestratorService,
                                ProductCategoryOptionRepository productCategoryOptionRepository) {
        this.marketingProperties = marketingProperties;
        this.omnichannelCommerceService = omnichannelCommerceService;
        this.orderRepository = orderRepository;
        this.offerRepository = offerRepository;
        this.productRepository = productRepository;
        this.productCategoryOptionRepository = productCategoryOptionRepository;
        this.leadRepository = leadRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.whatsAppMessageService = whatsAppMessageService;
        this.receiptSettingsRepository = receiptSettingsRepository;
        this.botOrchestratorService = botOrchestratorService;
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
        if (isDuplicateInbound(message)) {
            return WhatsAppBotWebhookResponse.builder()
                    .accepted(true)
                    .sent(false)
                    .message("Duplicate WhatsApp webhook ignored")
                    .customerPhone(message.from())
                    .replyText("")
                    .productCount(0)
                    .build();
        }
        OmnichannelLeadResponse lead = captureLead(message, payload);
        BotSession session = sessionFor(message.from());
        boolean moreRequested = isMoreRequest(normalize(message.text()));
        BotReply actionReply = buildProductActionReply(message.text(), session, lead, message.from());
        if (actionReply != null) {
            BotInboundMessage botInbound = buildBotInbound(message, lead);
            BotUnderstanding actionUnderstanding = new BotUnderstanding(
                    BotIntent.PRODUCT_SEARCH,
                    session.lastCategory(),
                    message.text(),
                    positiveAmountOrNull(session.lastMinPrice()),
                    positiveAmountOrNull(session.lastMaxPrice()),
                    null
            );
            updateSession(message.from(), message.text(), actionUnderstanding, actionReply, moreRequested);
            SendResult sendResult = sendReply(message.from(), actionReply);
            saveOutboundMessage(lead.getLeadId(), actionReply.text(), sendResult);
            rememberWithOrchestrator(botInbound, actionReply.text());

            return WhatsAppBotWebhookResponse.builder()
                    .accepted(true)
                    .sent(sendResult.success())
                    .message(sendResult.success() ? "Bot reply sent" : "Bot reply generated but sender is not configured or failed")
                    .leadId(lead.getLeadId())
                    .customerPhone(message.from())
                    .replyText(actionReply.text())
                    .productCount(actionReply.productCount())
                    .providerMessageId(sendResult.messageId())
                    .errorMessage(sendResult.errorMessage())
                    .build();
        }
        InboundMessage contextualMessage = contextualizeMessage(message, session);
        BotInboundMessage botInbound = buildBotInbound(contextualMessage, lead);
        BotIntentClassification classification = classifyWithOrchestrator(botInbound);
        BotUnderstanding understanding = mergeOrchestratedUnderstanding(contextualMessage.text(), understandMessage(contextualMessage.text()), classification);
        BotReply reply = buildReply(understanding, lead, message.from(), moreRequested);
        reply = polishReplyWithOrchestrator(botInbound, classification, reply);
        updateSession(message.from(), message.text(), understanding, reply, moreRequested);
        SendResult sendResult = sendReply(message.from(), reply);
        saveOutboundMessage(lead.getLeadId(), reply.text(), sendResult);
        rememberWithOrchestrator(botInbound, reply.text());

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

    private BotInboundMessage buildBotInbound(InboundMessage message, OmnichannelLeadResponse lead) {
        return BotInboundMessage.builder()
                .mobile(message.from())
                .customerName(message.name())
                .messageText(message.text())
                .messageId(message.messageId())
                .channel("WHATSAPP")
                .lead(lead)
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

    private BotSession sessionFor(String from) {
        cleanupBotSessions();
        String key = sessionKey(from);
        if (!hasText(key)) {
            return new BotSession(null, null, null, null, null, List.of(), Instant.now());
        }
        return BOT_SESSIONS.getOrDefault(key, new BotSession(null, null, null, null, null, List.of(), Instant.now()));
    }

    private InboundMessage contextualizeMessage(InboundMessage message, BotSession session) {
        if (message == null || session == null) {
            return message;
        }
        String rawText = defaultString(message.text(), "");
        String normalized = normalize(rawText);
        if (isBudgetOnly(normalized) && hasText(session.lastCategory())) {
            BigDecimal amount = extractPositiveAmount(normalized);
            if (amount != null) {
                String combined = session.lastCategory() + " under " + amount.stripTrailingZeros().toPlainString();
                return new InboundMessage(message.from(), message.name(), combined, message.messageId());
            }
        }
        if (isMoreRequest(normalized) && hasText(session.lastCategory())) {
            StringBuilder combined = new StringBuilder(session.lastCategory());
            BigDecimal maxPrice = positiveAmountOrNull(session.lastMaxPrice());
            if (maxPrice != null) {
                combined.append(" under ").append(maxPrice.stripTrailingZeros().toPlainString());
            }
            return new InboundMessage(message.from(), message.name(), combined.toString(), message.messageId());
        }
        if (hasText(session.lastCategory()) && isCategoryModifierOnly(normalized)) {
            return new InboundMessage(message.from(), message.name(), session.lastCategory() + " " + rawText, message.messageId());
        }
        if (containsAny(normalized, "buy", "view", "details", "similar") && hasText(session.lastCategory()) && !hasRecognizableCatalogSignal(normalized)) {
            return new InboundMessage(message.from(), message.name(), rawText + " " + session.lastCategory(), message.messageId());
        }
        return message;
    }

    private void updateSession(String from, String rawText, BotUnderstanding understanding, BotReply reply, boolean moreRequested) {
        String key = sessionKey(from);
        if (!hasText(key) || understanding == null) {
            return;
        }
        BotSession previous = sessionFor(from);
        String category = firstNonBlank(understanding.category(), previous.lastCategory());
        BigDecimal maxPrice = positiveAmountOrNull(understanding.maxPrice());
        if (maxPrice == null) {
            maxPrice = positiveAmountOrNull(previous.lastMaxPrice());
        }
        BigDecimal minPrice = positiveAmountOrNull(understanding.minPrice());
        if (minPrice == null) {
            minPrice = positiveAmountOrNull(previous.lastMinPrice());
        }
        String intent = understanding.intent() == null ? previous.lastIntent() : understanding.intent().name();
        String recentProduct = reply == null || reply.products() == null || reply.products().isEmpty()
                ? previous.lastProductId()
                : reply.products().get(0).getProductId() == null ? previous.lastProductId() : reply.products().get(0).getProductId().toString();
        if (understanding.intent() == BotIntent.CATEGORY_BROWSE && hasText(detectCategory(normalize(rawText)))) {
            category = detectCategory(normalize(rawText));
        }
        boolean keepShownHistory = moreRequested || categoryLooksLike(category, previous.lastCategory());
        List<UUID> history = mergeShownProductHistory(previous.shownProductHistory(), reply, keepShownHistory);
        BOT_SESSIONS.put(key, new BotSession(category, maxPrice, minPrice, intent, recentProduct, history, Instant.now()));
    }

    private List<UUID> mergeShownProductHistory(List<UUID> previous, BotReply reply, boolean keepPrevious) {
        List<UUID> base = keepPrevious && previous != null && !previous.isEmpty()
                ? new ArrayList<>(previous)
                : new ArrayList<>();
        if (reply == null || reply.products() == null) {
            return List.copyOf(base);
        }
        for (OmnichannelProductCardResponse card : reply.products()) {
            if (card.getProductId() == null) {
                continue;
            }
            if (!base.contains(card.getProductId())) {
                base.add(card.getProductId());
            }
            if (base.size() >= SHOWN_PRODUCT_HISTORY_CAP) {
                break;
            }
        }
        return List.copyOf(base);
    }

    private void cleanupBotSessions() {
        Instant cutoff = Instant.now().minus(BOT_SESSION_TTL);
        BOT_SESSIONS.entrySet().removeIf(entry -> entry.getValue().updatedAt().isBefore(cutoff));
    }

    private String sessionKey(String from) {
        String digits = digitsOnly(from);
        if (digits.length() >= 10) {
            return digits.substring(digits.length() - 10);
        }
        return digits;
    }

    private BotReply buildReply(BotUnderstanding understanding, OmnichannelLeadResponse lead, String from, boolean moreRequested) {
        if (understanding.intent() == BotIntent.GREETING) {
            return buildWelcomeReply();
        }
        if (understanding.intent() == BotIntent.CATEGORY_BROWSE) {
            if (hasText(understanding.category())) {
                return buildProductSearchReply(new BotUnderstanding(
                        BotIntent.PRODUCT_SEARCH,
                        understanding.category(),
                        firstNonBlank(understanding.searchText(), understanding.category()),
                        understanding.minPrice(),
                        understanding.maxPrice(),
                        understanding.occasion()
                ), lead, from, false);
            }
            if (isShowMoreSelection(understanding.searchText())) {
                return buildShowMoreReply();
            }
            return new BotReply(
                    buildCategoryMenuReply(),
                    0,
                    List.of(),
                    null,
                    null,
                    List.of(),
                    categorySections(),
                    "Browse Categories",
                    "Choose category"
            );
        }
        if (understanding.intent() == BotIntent.ORDER_SUPPORT) {
            return buildOrderSupportBotReply(understanding.searchText(), lead);
        }
        if (understanding.intent() == BotIntent.PAYMENT_SUPPORT) {
            return new BotReply(buildPaymentSupportReply(understanding.searchText(), lead), 0, List.of());
        }
        if (understanding.intent() == BotIntent.OFFER_INQUIRY) {
            return new BotReply(buildOfferReply(understanding.category()), 0, List.of());
        }
        if (understanding.intent() == BotIntent.HUMAN_HANDOFF) {
            return new BotReply("I am connecting you to our support team. Please share your order number or requirement, and I will keep the details ready for the agent.", 0, List.of());
        }
        if (understanding.intent() == BotIntent.THANKS) {
            return new BotReply("Most welcome. Tell me your budget or category whenever you want to see more options.", 0, List.of());
        }
        if (understanding.intent() == BotIntent.UNKNOWN) {
            return new BotReply(buildFallbackMenuReply(), 0, List.of());
        }

        return buildProductSearchReply(understanding, lead, from, moreRequested);
    }

    private BotReply buildProductSearchReply(BotUnderstanding understanding, OmnichannelLeadResponse lead, String from, boolean moreRequested) {
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
        searchRequest.setInStockOnly(wantsOnlyReadyStock(understanding));
        searchRequest.setLimit(5);
        if (shouldExcludeShownProducts(moreRequested, from, understanding.category())) {
            BotSession session = sessionFor(from);
            if (session.shownProductHistory() != null && !session.shownProductHistory().isEmpty()) {
                searchRequest.setExcludeProductIds(new ArrayList<>(session.shownProductHistory()));
            }
        }

        OmnichannelProductSearchResponse products = omnichannelCommerceService.searchProducts(searchRequest);
        List<OmnichannelProductCardResponse> productCards = filterProductCardsForRequestedCategory(
                products == null || products.getProducts() == null ? List.of() : products.getProducts(),
                understanding.category()
        );
        if (products == null || productCards.isEmpty()) {
            products = fallbackProductSearch(searchRequest, understanding);
            productCards = filterProductCardsForRequestedCategory(
                    products.getProducts() == null ? List.of() : products.getProducts(),
                    understanding.category()
            );
        }
        if (moreRequested && productCards.isEmpty()) {
            return new BotReply(
                    "No more products available in this category.",
                    0,
                    List.of(),
                    null,
                    null,
                    List.of(
                            new WhatsAppInteractiveOption("View Collections", "Collections", "Browse categories"),
                            new WhatsAppInteractiveOption("Talk to Shop", "Talk to Shop", "Ask store")
                    ),
                    List.of(),
                    null,
                    null
            );
        }
        products = withProductCards(products, productCards);
        List<WhatsAppInteractiveOption> productButtons = productActionButtons(productCards);
        return new BotReply(
                formatProductReply(products, understanding),
                productCards.size(),
                productCards,
                null,
                null,
                productButtons,
                List.of(),
                null,
                null
        );
    }

    private BotReply buildProductActionReply(String rawText, BotSession session, OmnichannelLeadResponse lead, String from) {
        String normalized = normalize(rawText);
        if (!hasText(normalized)) {
            return null;
        }
        if (isMoreRequest(normalized) && session != null && hasText(session.lastCategory())) {
            return buildProductSearchReply(new BotUnderstanding(
                    BotIntent.PRODUCT_SEARCH,
                    session.lastCategory(),
                    session.lastCategory(),
                    positiveAmountOrNull(session.lastMinPrice()),
                    positiveAmountOrNull(session.lastMaxPrice()),
                    null
            ), lead, from, true);
        }

        boolean isProductAction = containsAny(normalized, "view", "detail", "buy", "add", "cart", "select");
        if (!isProductAction) {
            return null;
        }
        Optional<Product> product = findProductForAction(rawText, session);
        if (product.isEmpty()) {
            return null;
        }
        if (containsAny(normalized, "buy", "add", "cart", "select")) {
            return buildAddToCartReply(product.get());
        }
        if (containsAny(normalized, "view", "detail")) {
            return buildProductDetailReply(product.get());
        }
        if (containsAny(normalized, "more image", "more images", "gallery")) {
            return buildProductDetailReply(product.get());
        }
        return null;
    }

    private Optional<Product> findProductForAction(String rawText, BotSession session) {
        String productCode = extractProductActionCode(rawText);
        if (hasText(productCode)) {
            String upperCode = productCode.replace("-", "").toUpperCase(Locale.ROOT);
            try {
                Optional<Product> exact = productRepository.findAll().stream()
                        .filter(product -> product.getId() != null)
                        .filter(product -> product.getId().toString().replace("-", "").toUpperCase(Locale.ROOT).startsWith(upperCode))
                        .findFirst();
                if (exact.isPresent()) {
                    return exact;
                }
            } catch (Exception exception) {
                log.debug("Unable to resolve WhatsApp product action code {}", productCode, exception);
            }
        }
        if (session != null && hasText(session.lastProductId())) {
            try {
                return productRepository.findById(UUID.fromString(session.lastProductId()));
            } catch (IllegalArgumentException exception) {
                log.debug("Ignoring invalid WhatsApp session product id {}", session.lastProductId());
            }
        }
        return Optional.empty();
    }

    private String extractProductActionCode(String rawText) {
        String text = defaultString(rawText, "");
        Matcher actionMatcher = Pattern.compile("\\b(?:VIEW|BUY|DETAILS|DETAIL|ADD|SELECT)\\s+([A-Fa-f0-9-]{6,36})\\b", Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (actionMatcher.find()) {
            return actionMatcher.group(1).replace("-", "");
        }
        for (String token : text.split("\\s+")) {
            String cleaned = token.replaceAll("[^A-Fa-f0-9-]", "");
            if (cleaned.matches("[A-Fa-f0-9]{6,36}") || cleaned.matches("[A-Fa-f0-9-]{8,36}")) {
                return cleaned.replace("-", "");
            }
        }
        return "";
    }

    private BotReply buildProductDetailReply(Product product) {
        OmnichannelProductCardResponse card = mapFallbackProduct(product, new OmnichannelProductSearchRequest());
        String text = "Product Details\n"
                + defaultString(card.getName(), "Product") + "\n"
                + "Offer Price: " + formatPrice(card.getPrice()) + "\n"
                + defaultString(card.getStockLabel(), "Available") + " | " + productTags(card) + "\n\n"
                + "AI Suggestion: You may also like matching earrings and bangles to complete the look.";
        return new BotReply(
                text,
                1,
                List.of(card),
                null,
                null,
                List.of(),
                productDetailSections(card),
                "Product Actions",
                "Choose"
        );
    }

    private BotReply buildAddToCartReply(Product product) {
        OmnichannelProductCardResponse card = mapFallbackProduct(product, new OmnichannelProductSearchRequest());
        String cartUrl = firstNonBlank(card.getBuyNowUrl(), card.getCheckoutUrl(), shortProductLink(card));
        String text = "Cart\n"
                + "Product: " + defaultString(card.getName(), "Product") + "\n"
                + "Quantity: 1\n"
                + "Offer discount: " + discountLabel(card) + "\n"
                + "Total: " + formatPrice(card.getPrice()) + "\n\n"
                + "Checkout: " + cartUrl;
        return new BotReply(
                text,
                1,
                List.of(),
                null,
                null,
                List.of(
                        new WhatsAppInteractiveOption("Checkout", "Checkout", "Open cart"),
                        new WhatsAppInteractiveOption("Support", "Support", "Need help")
                ),
                List.of(),
                null,
                null
        );
    }

    private BotReply buildWelcomeReply() {
        String text = "Namaskar! Welcome to Krishnai Pearl Shopee.\n"
                + "Tell me what you want or tap an option below. I understand Marathi, Hindi, English and spelling mistakes.";
        return new BotReply(
                text,
                0,
                List.of(),
                welcomeImageUrl(),
                "Namaste. I can help with shopping, orders, offers, payments, and support.",
                List.of(),
                mainMenuSections(),
                "Krishnai Assistant",
                "Choose"
        );
    }

    private BotReply buildShowMoreReply() {
        return new BotReply(
                "More options below.",
                0,
                List.of(),
                null,
                null,
                List.of(),
                showMoreMenuSections(),
                "Krishnai Assistant",
                "Show More"
        );
    }

    private String buildGuidedMenuReply() {
        return "Namaste. Welcome to Krishnai Pearl Shopee.\n"
                + "I can help like a shop salesperson with products, orders, delivery, payments, offers, and support.\n\n"
                + "Reply with one option:\n"
                + "1. View Collections\n"
                + "2. Offers\n"
                + "3. Track Order\n"
                + "4. Talk to Shop\n"
                + "5. Show More\n\n"
                + "You can also type naturally, like: necklace under 1500, bridal set, payment status, or my latest order.";
    }

    private String buildCategoryMenuReply() {
        List<String> categories = availableCategories().stream().limit(6).toList();
        String categoryText = categories.isEmpty()
                ? "- New categories will appear here as products are added."
                : String.join("\n", categories.stream().map(category -> "- " + displayCategoryName(category)).toList());
        return "Sure. Choose a category, or send your budget/style and I will shortlist the best pieces:\n\n"
                + categoryText + "\n\n"
                + "Quick examples:\n"
                + "- necklace under 1500\n"
                + "- bridal jewellery\n"
                + "- cosmetics gift under 1000\n\n"
                + "Need help deciding? Reply: Connect to Agent";
    }

    private List<WhatsAppInteractiveSection> mainMenuSections() {
        return List.of(new WhatsAppInteractiveSection("Main menu", List.of(
                new WhatsAppInteractiveOption("View Collections", "View Collections", "Browse product categories"),
                new WhatsAppInteractiveOption("Offers", "Offers", "Active coupons and deals"),
                new WhatsAppInteractiveOption("Track Order", "Track Order", "Order and delivery status"),
                new WhatsAppInteractiveOption("Show More", "Show More", "More categories and cart")
        )));
    }

    private List<WhatsAppInteractiveOption> mainMenuButtons() {
        return List.of(
                new WhatsAppInteractiveOption("View Collections", "View Collections", "Browse products"),
                new WhatsAppInteractiveOption("Offers", "Offers", "Active deals"),
                new WhatsAppInteractiveOption("Track Order", "Track Order", "Order status")
        );
    }

    private List<WhatsAppInteractiveSection> showMoreMenuSections() {
        List<WhatsAppInteractiveOption> options = new ArrayList<>();
        options.add(new WhatsAppInteractiveOption("Talk to Shop", "Talk to Shop", "Connect with store"));
        availableCategories().stream()
                .limit(6)
                .map(category -> new WhatsAppInteractiveOption(category, displayCategoryName(category), displayCategoryDescription(category)))
                .forEach(options::add);
        options.add(new WhatsAppInteractiveOption("My Cart", "My Cart", "Cart and checkout"));
        options.add(new WhatsAppInteractiveOption("Support", "Support", "Need help"));
        return List.of(new WhatsAppInteractiveSection("Show More", options));
    }

    private List<WhatsAppInteractiveSection> categorySections() {
        List<WhatsAppInteractiveOption> categoryOptions = availableCategories().stream()
                .limit(10)
                .map(category -> new WhatsAppInteractiveOption(category, displayCategoryName(category), displayCategoryDescription(category)))
                .toList();
        if (categoryOptions.isEmpty()) {
            return List.of();
        }
        return List.of(new WhatsAppInteractiveSection("Categories", categoryOptions));
    }

    private String displayCategoryName(String category) {
        String configuredName = configuredCategoryDisplayName(category);
        if (hasText(configuredName)) {
            return configuredName;
        }
        String normalized = normalize(category);
        String canonical = canonicalCategoryTerm(normalized);
        if (hasText(canonical) && !canonical.equalsIgnoreCase(category)) {
            return titleCaseLabel(canonical);
        }
        return titleCaseLabel(category);
    }

    private String displayCategoryDescription(String category) {
        return "View " + displayCategoryName(category) + " products";
    }

    private String titleCaseLabel(String value) {
        String normalized = safe(value).replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
        if (!hasText(normalized)) {
            return "Products";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : normalized.split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private List<WhatsAppInteractiveOption> productActionButtons(List<OmnichannelProductCardResponse> products) {
        if (products == null || products.isEmpty()) {
            return List.of(
                    new WhatsAppInteractiveOption("Browse Categories", "Categories", "Browse collections"),
                    new WhatsAppInteractiveOption("Connect to Agent", "Agent", "Talk to support")
            );
        }
        OmnichannelProductCardResponse first = products.get(0);
        String productCode = shortProductCode(first);
        return List.of(
                new WhatsAppInteractiveOption("VIEW " + productCode, "Details", "Price, stock, offers"),
                new WhatsAppInteractiveOption("BUY " + productCode, "Add to Cart", "Add first item"),
                new WhatsAppInteractiveOption("MORE", "Yes, show more", "Next 5 products")
        );
    }

    private List<WhatsAppInteractiveSection> productSections(List<OmnichannelProductCardResponse> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }

        List<OmnichannelProductCardResponse> visibleProducts = products.stream()
                .limit(PRODUCT_IMAGE_SEND_LIMIT)
                .toList();
        List<WhatsAppInteractiveOption> detailOptions = visibleProducts.stream()
                .map(product -> new WhatsAppInteractiveOption(
                        "VIEW " + shortProductCode(product),
                        interactiveTitle(hasText(product.getName()) ? product.getName() : "View Product"),
                        "Details | " + formatPrice(product.getPrice())
                ))
                .toList();
        List<WhatsAppInteractiveOption> actionOptions = new ArrayList<>();
        for (OmnichannelProductCardResponse product : visibleProducts) {
            actionOptions.add(new WhatsAppInteractiveOption(
                    "BUY " + shortProductCode(product),
                    interactiveTitle("Add " + firstProductWord(product)),
                    defaultString(product.getStockLabel(), "Available now")
            ));
        }
        actionOptions.add(new WhatsAppInteractiveOption("MORE", "More Similar", "Show next matching items"));

        if (detailOptions.isEmpty()) {
            return List.of();
        }
        return List.of(
                new WhatsAppInteractiveSection("Product Details", detailOptions),
                new WhatsAppInteractiveSection("Cart and More", actionOptions)
        );
    }

    private String firstProductWord(OmnichannelProductCardResponse product) {
        String name = product == null ? null : product.getName();
        if (!hasText(name)) {
            return "Item";
        }
        String[] words = name.trim().split("\\s+");
        return words.length == 0 ? "Item" : words[0];
    }

    private String interactiveTitle(String value) {
        String title = defaultString(value, "Product").trim();
        return title.length() <= 24 ? title : title.substring(0, 23).trim();
    }

    private List<WhatsAppInteractiveSection> productDetailSections(OmnichannelProductCardResponse product) {
        String productCode = shortProductCode(product);
        return List.of(new WhatsAppInteractiveSection("Actions", List.of(
                new WhatsAppInteractiveOption("BUY " + productCode, "Add to Cart", "Add 1 qty"),
                new WhatsAppInteractiveOption("BUY " + productCode, "Buy Now", "Checkout"),
                new WhatsAppInteractiveOption("MORE_IMAGES " + productCode, "More Images", "See gallery"),
                new WhatsAppInteractiveOption("Talk to Shop", "Talk to Shop", "Ask store")
        )));
    }

    private String buildFallbackMenuReply() {
        return "I want to help with that. I can show products, orders, delivery status, payments, offers, or connect you to an agent.\n\n"
                + "Try one of these:\n"
                + "- Show necklaces under 1500\n"
                + "- My orders\n"
                + "- Payment status\n"
                + "- Current offers\n"
                + "- Connect to Agent";
    }

    private String buildOrderSupportReply(String text, OmnichannelLeadResponse lead) {
        return buildOrderSupportBotReply(text, lead).text();
    }

    private BotReply buildOrderSupportBotReply(String text, OmnichannelLeadResponse lead) {
        Optional<CustomerOrder> orderByNumber = findOrderByNumber(text);
        if (orderByNumber.isPresent()) {
            CustomerOrder order = orderByNumber.get();
            List<OmnichannelProductCardResponse> itemCards = orderItemCards(order);
            return new BotReply(formatOrderCard(order, true), itemCards.size(), itemCards);
        }
        List<CustomerOrder> orders = findRecentOrdersByMobile(firstNonBlank(text, lead == null ? null : lead.getMobile(), lead == null ? null : lead.getExternalUserId()));
        if (!orders.isEmpty()) {
            StringBuilder reply = new StringBuilder();
            if (isOrderSummaryQuestion(text)) {
                BigDecimal totalSpent = orders.stream()
                        .map(CustomerOrder::getFinalAmount)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                reply.append("I found ").append(orders.size()).append(" recent order");
                if (orders.size() != 1) {
                    reply.append("s");
                }
                reply.append(" for this WhatsApp number.\n")
                        .append("Recent order value: ").append(formatPrice(totalSpent)).append("\n\n");
            } else {
                reply.append("Here are your recent orders:\n\n");
            }
            for (CustomerOrder order : orders) {
                reply.append(formatOrderCard(order, false)).append("\n\n");
            }
            reply.append("Reply with an order number for full details, or type Track Order / Reorder / Connect to Agent.");
            return new BotReply(reply.toString().trim(), 0, List.of());
        }
        return new BotReply("I could not find an order for this WhatsApp number yet. Please share your order number like KPS100 or your registered mobile number.\n\nNext actions: Track Order, Payment Status, Connect to Agent.", 0, List.of());
    }

    private String buildPaymentSupportReply(String text, OmnichannelLeadResponse lead) {
        Optional<CustomerOrder> orderByNumber = findOrderByNumber(text);
        CustomerOrder order = orderByNumber.orElseGet(() -> findRecentOrdersByMobile(firstNonBlank(text, lead == null ? null : lead.getMobile(), lead == null ? null : lead.getExternalUserId())).stream().findFirst().orElse(null));
        if (order == null) {
            return "I can check payment or refund status for you. Please share your order number or registered mobile number.\n\nNext actions: Retry Payment, My Orders, Connect to Agent.";
        }
        return "Payment details\n"
                + "Order: " + defaultString(order.getOrderNumber(), "-") + "\n"
                + "Amount: " + formatPrice(order.getFinalAmount()) + "\n"
                + "Payment status: " + defaultString(order.getPaymentStatus(), "Not available") + "\n"
                + "Payment method: " + defaultString(order.getPaymentGateway(), "Not available") + "\n"
                + "Transaction ID: " + defaultString(order.getPaymentId(), "Not available") + "\n\n"
                + "Next actions: Track Order, Retry Payment, Connect to Agent.";
    }

    private String buildOfferReply(String category) {
        List<Offer> offers = offerRepository.findActiveOffers(LocalDate.now()).stream()
                .filter(offer -> !hasText(category) || !hasText(offer.getCategory()) || categoryLooksLike(category, offer.getCategory()))
                .limit(5)
                .toList();
        if (offers.isEmpty()) {
            return "I do not see an active offer for that right now. I can still show best picks by budget or category.\n\nTry: necklace under 1500, cosmetics gift, or Connect to Agent.";
        }
        StringBuilder reply = new StringBuilder("Current offers:\n\n");
        int index = 1;
        for (Offer offer : offers) {
            reply.append(index++).append(". ").append(defaultString(offer.getName(), "Special offer")).append("\n")
                    .append(formatOfferValue(offer)).append("\n");
            if (hasText(offer.getCouponCode())) {
                reply.append("Coupon: ").append(offer.getCouponCode()).append("\n");
            }
            if (hasText(offer.getCategory())) {
                reply.append("Category: ").append(offer.getCategory()).append("\n");
            }
            if (offer.getProduct() != null && hasText(offer.getProduct().getName())) {
                reply.append("Product: ").append(offer.getProduct().getName()).append("\n");
            }
            reply.append("Valid till: ").append(offer.getEndDate()).append("\n\n");
        }
        reply.append("Reply with a category or budget to see eligible products.");
        return reply.toString().trim();
    }

    private Optional<CustomerOrder> findOrderByNumber(String text) {
        if (!hasText(text)) {
            return Optional.empty();
        }
        Matcher matcher = ORDER_NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return orderRepository.findByOrderNumberIgnoreCase(matcher.group().trim());
    }

    private List<CustomerOrder> findRecentOrdersByMobile(String text) {
        String digits = digitsOnly(text);
        if (digits.length() < 10) {
            return List.of();
        }
        String lastTenDigits = digits.substring(digits.length() - 10);
        return orderRepository.findTop3ByCustomer_MobileContainingOrderByCreatedAtDesc(lastTenDigits);
    }

    private boolean isOrderSummaryQuestion(String text) {
        String normalized = normalize(text);
        return containsAny(normalized, "total", "spent", "how many", "history", "previous", "all orders", "last 5", "recent");
    }

    private String deliveryProgress(CustomerOrder order) {
        String status = order.getStatus() == null ? "" : order.getStatus().name();
        int currentStep = switch (status) {
            case "CREATED", "PENDING" -> 1;
            case "CONFIRMED" -> 1;
            case "PACKED" -> 2;
            case "SHIPPED", "DISPATCHED", "OUT_FOR_DELIVERY" -> 3;
            case "DELIVERED", "COMPLETED" -> 4;
            default -> 1;
        };
        List<String> steps = List.of("Placed", "Packed", "Shipped", "Delivered");
        StringBuilder progress = new StringBuilder("Delivery progress\n");
        progress.append(progressBar(currentStep, steps.size())).append("\n");
        for (int index = 0; index < steps.size(); index++) {
            progress.append(index < currentStep ? "✓ " : "○ ").append(steps.get(index)).append("\n");
        }
        if (containsAny(normalize(status), "cancel", "return", "refund")) {
            progress.append("Current exception: ").append(titleCaseLabel(status));
        }
        return progress.toString().trim();
    }

    private String progressBar(int currentStep, int totalSteps) {
        StringBuilder bar = new StringBuilder();
        for (int index = 1; index <= totalSteps; index++) {
            if (index > 1) {
                bar.append(index <= currentStep ? "━━━" : "───");
            }
            bar.append(index <= currentStep ? "●" : "○");
        }
        return bar.toString();
    }

    private String formatOfferValue(Offer offer) {
        BigDecimal value = offer.getDiscountValue() == null ? offer.getValue() : offer.getDiscountValue();
        String type = offer.getDiscountType() == null
                ? (offer.getType() == null ? "" : offer.getType().name())
                : offer.getDiscountType().name();
        if (containsAny(normalize(type), "percent")) {
            return value == null ? "Discount available" : value.stripTrailingZeros().toPlainString() + "% off";
        }
        if (value == null) {
            return "Discount available";
        }
        return formatPrice(value) + " off";
    }

    private String formatOrderCard(CustomerOrder order, boolean includeItems) {
        StringBuilder reply = new StringBuilder();
        reply.append("Order ID: ").append(defaultString(order.getOrderNumber(), "-")).append("\n")
                .append("Date: ").append(order.getCreatedAt() == null ? "-" : order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))).append("\n")
                .append("Amount: ").append(formatPrice(order.getFinalAmount())).append("\n")
                .append("Status: ").append(defaultString(order.getStatus() == null ? null : order.getStatus().name(), "Not available")).append("\n")
                .append("Payment: ").append(defaultString(order.getPaymentStatus(), "Not available")).append("\n")
                .append(deliveryProgress(order)).append("\n");
        if (includeItems && order.getItems() != null && !order.getItems().isEmpty()) {
            reply.append("Being delivered:\n");
            order.getItems().stream().limit(4).forEach(item ->
                    reply.append("- ").append(item.getProductName())
                            .append(" x").append(item.getQuantity())
                            .append(" = ").append(formatPrice(item.getLineTotal()))
                            .append("\n")
            );
        }
        reply.append("Actions: Track Order, Show Items, Reorder, Invoice, Connect to Agent");
        return reply.toString().trim();
    }

    private List<OmnichannelProductCardResponse> orderItemCards(CustomerOrder order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            return List.of();
        }
        return order.getItems().stream()
                .limit(4)
                .map(item -> {
                    Product product = item.getProduct();
                    return OmnichannelProductCardResponse.builder()
                            .productId(product == null ? null : product.getId())
                            .name(defaultString(item.getProductName(), "Order item"))
                            .category(item.getCategory())
                            .price(item.getPrice())
                            .quantity(item.getQuantity())
                            .inStock(true)
                            .stockLabel("In this delivery")
                            .imageUrl(product == null ? null : publicImageUrl(product.getImageDataUrl()))
                            .shortBenefit("Qty " + defaultString(item.getQuantity() == null ? null : item.getQuantity().toString(), "1"))
                            .build();
                })
                .filter(card -> hasText(publicImageUrl(card.getImageUrl())))
                .toList();
    }

    private String formatProductReply(OmnichannelProductSearchResponse response, BotUnderstanding understanding) {
        List<OmnichannelProductCardResponse> products = response.getProducts() == null ? List.of() : response.getProducts();
        if (products.isEmpty()) {
            return "I could not find a close match yet. Please try another word, category, or budget like \"necklace under 1500\", \"bangles\", or \"cosmetics gift\".";
        }

        if (products.stream().anyMatch(product -> hasText(publicImageUrl(product.getImageUrl())))) {
            StringBuilder intro = new StringBuilder();
            intro.append("I found ").append(products.size()).append(" good match");
            if (products.size() != 1) {
                intro.append("es");
            }
            intro.append(". Showing first ").append(Math.min(products.size(), PRODUCT_IMAGE_SEND_LIMIT)).append(" products.");
            if (products.size() > 1) {
                intro.append("\n\nOther close picks: ");
                intro.append(products.stream()
                        .skip(1)
                        .limit(3)
                        .map(OmnichannelProductCardResponse::getName)
                        .filter(this::hasText)
                        .collect(java.util.stream.Collectors.joining(", ")));
            }
            intro.append("\n\nAfter these products, choose Details, Add to Cart, or Yes, show more. Reply No to stop.");
            return intro.toString().trim();
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

        boolean hasOutOfStock = products.stream().anyMatch(product -> Boolean.FALSE.equals(product.getInStock()));
        if (hasOutOfStock) {
            reply.append("Some matching items are currently out of stock, so I have marked availability clearly.\n\n");
        }

        int index = 1;
        for (OmnichannelProductCardResponse product : products) {
            reply.append(index++).append(". ").append(product.getName()).append("\n")
                    .append(formatPrice(product.getPrice())).append(" | ").append(defaultString(product.getStockLabel(), "Available")).append("\n");
            if (hasText(product.getShortBenefit())) {
                reply.append(product.getShortBenefit()).append("\n");
            }
            reply.append("Reply BUY ").append(shortProductCode(product))
                    .append(" to add it to cart, or VIEW ")
                    .append(shortProductCode(product))
                    .append(" for full details.\n\n");
        }
        reply.append("Do you want to see more products? Tap Yes, show more or reply No.");
        return reply.toString().trim();
    }

    private boolean wantsOnlyReadyStock(BotUnderstanding understanding) {
        String text = normalize(understanding == null ? null : understanding.searchText());
        return containsAny(text,
                "in stock",
                "instock",
                "ready stock",
                "available now",
                "stock available",
                "only available",
                "show available",
                "उपलब्ध");
    }

    private BotUnderstanding understandMessage(String text) {
        BotUnderstanding ai = understandWithAi(text);
        if (ai != null && ai.intent() != BotIntent.UNKNOWN) {
            return enrichProductUnderstanding(text, ai);
        }
        return enrichProductUnderstanding(text, understandWithRules(text));
    }

    private BotIntentClassification classifyWithOrchestrator(BotInboundMessage inbound) {
        if (botOrchestratorService == null || inbound == null) {
            return null;
        }
        try {
            return botOrchestratorService.classify(inbound);
        } catch (Exception exception) {
            log.debug("WhatsApp bot orchestrator classification failed", exception);
            return null;
        }
    }

    private void rememberWithOrchestrator(BotInboundMessage inbound, String outboundText) {
        if (botOrchestratorService == null || inbound == null) {
            return;
        }
        try {
            botOrchestratorService.remember(inbound, outboundText);
        } catch (Exception exception) {
            log.debug("WhatsApp bot memory write failed", exception);
        }
    }

    private BotUnderstanding mergeOrchestratedUnderstanding(String originalText,
                                                            BotUnderstanding fallback,
                                                            BotIntentClassification classification) {
        if (classification == null || classification.getIntent() == null || classification.getIntent() == WhatsAppBotIntent.FALLBACK) {
            return fallback;
        }
        BotIntent mappedIntent = switch (classification.getIntent()) {
            case WELCOME_MENU -> BotIntent.GREETING;
            case BROWSE_CATEGORIES -> BotIntent.CATEGORY_BROWSE;
            case SEARCH_PRODUCTS, PRODUCT_DETAILS, PRODUCT_RECOMMENDATION -> BotIntent.PRODUCT_SEARCH;
            case OFFERS_AND_COUPONS -> BotIntent.OFFER_INQUIRY;
            case ORDER_HISTORY, LATEST_ORDER, ORDER_DETAILS, TOTAL_ORDER_VALUE, ORDER_COUNT, DELIVERY_STATUS, REORDER -> BotIntent.ORDER_SUPPORT;
            case PAYMENT_STATUS, REFUND_STATUS -> BotIntent.PAYMENT_SUPPORT;
            case ACCOUNT_HELP, CART_CHECKOUT_HELP, AGENT_HANDOFF -> BotIntent.HUMAN_HANDOFF;
            case FALLBACK -> fallback == null ? BotIntent.UNKNOWN : fallback.intent();
        };
        if (mappedIntent == BotIntent.UNKNOWN) {
            return fallback;
        }
        String category = firstNonBlank(classification.getCategory(), fallback == null ? null : fallback.category());
        String occasion = firstNonBlank(classification.getOccasion(), fallback == null ? null : fallback.occasion());
        String searchText = firstNonBlank(classification.getSearchText(), fallback == null ? null : fallback.searchText(), originalText);
        BigDecimal minPrice = classification.getMinPrice() != null ? classification.getMinPrice() : fallback == null ? null : fallback.minPrice();
        BigDecimal maxPrice = classification.getMaxPrice() != null ? classification.getMaxPrice() : fallback == null ? null : fallback.maxPrice();
        BotUnderstanding merged = new BotUnderstanding(mappedIntent, category, searchText, minPrice, maxPrice, occasion);
        return mappedIntent == BotIntent.PRODUCT_SEARCH ? enrichProductUnderstanding(originalText, merged) : merged;
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
                            {"intent":"PRODUCT_SEARCH|CATEGORY_BROWSE|ORDER_SUPPORT|PAYMENT_SUPPORT|OFFER_INQUIRY|GREETING|HUMAN_HANDOFF|THANKS|UNKNOWN","category":string|null,"searchText":string,"minPrice":number|null,"maxPrice":number|null,"occasion":string|null}
                            Extract category and price from English, Hindi, Hinglish, or Marathi.
                            Correct obvious customer spelling mistakes before returning searchText/category, for example neckalce/neckalace/neckless -> necklace, earings -> earrings, bangels -> bangles.
                            Examples:
                            "show earrings under 2000" -> PRODUCT_SEARCH, category earrings, maxPrice 2000
                            "show categories" -> CATEGORY_BROWSE
                            "any offers" -> OFFER_INQUIRY
                            "payment failed" -> PAYMENT_SUPPORT
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
        BotUnderstanding menuSelection = understandMenuSelection(normalized, text);
        if (menuSelection != null) {
            return menuSelection;
        }
        if (isGreeting(normalized)) {
            return new BotUnderstanding(BotIntent.GREETING, null, text, null, null, null);
        }
        if (isGuidedMenuRequest(normalized)) {
            return new BotUnderstanding(BotIntent.GREETING, null, text, null, null, null);
        }
        if (containsAny(normalized, "thanks", "thank you", "धन्यवाद", "shukriya")) {
            return new BotUnderstanding(BotIntent.THANKS, null, text, null, null, null);
        }
        if (isCategoryBrowseRequest(normalized)) {
            String category = detectCategory(normalized);
            return new BotUnderstanding(BotIntent.CATEGORY_BROWSE, category, text, null, null, null);
        }
        if (containsAny(normalized, "offer", "offers", "discount", "coupon", "deal", "सूट")) {
            String category = detectCategory(normalized);
            return new BotUnderstanding(BotIntent.OFFER_INQUIRY, category, text, null, null, null);
        }
        if (containsAny(normalized, "payment", "paid", "unpaid", "refund", "transaction", "failed", "retry", "razorpay", "pay", "पेमेंट")) {
            return new BotUnderstanding(BotIntent.PAYMENT_SUPPORT, null, text, null, null, null);
        }
        if (containsAny(normalized, "order", "orders", "delivery", "tracking", "track", "status", "return", "invoice", "bill", "reorder", "ऑर्डर")) {
            return new BotUnderstanding(BotIntent.ORDER_SUPPORT, null, text, null, null, null);
        }
        if (containsAny(normalized, "human", "person", "call", "staff", "salesperson", "owner", "complaint", "issue", "problem", "support", "agent")) {
            return new BotUnderstanding(BotIntent.HUMAN_HANDOFF, null, text, null, null, null);
        }

        String category = detectCategory(normalized);
        PriceRange priceRange = extractPriceRange(normalized);
        String occasion = detectOccasion(normalized);
        String searchText = buildSearchText(text, category, occasion);
        return new BotUnderstanding(BotIntent.PRODUCT_SEARCH, category, searchText, priceRange.min(), priceRange.max(), occasion);
    }

    private BotUnderstanding understandMenuSelection(String normalizedText, String originalText) {
        String value = normalize(normalizedText);
        return switch (value) {
            case "1", "one", "shop products", "shop product", "view collections", "collections", "view collection" ->
                    new BotUnderstanding(BotIntent.PRODUCT_SEARCH, null, "featured trending new arrival jewellery cosmetics", null, null, null);
            case "2", "two", "offers", "offer" ->
                    new BotUnderstanding(BotIntent.OFFER_INQUIRY, null, firstNonBlank(originalText, "offers"), null, null, null);
            case "3", "three", "track order", "track delivery", "delivery", "my orders", "orders", "my cart", "cart", "checkout" ->
                    new BotUnderstanding(BotIntent.ORDER_SUPPORT, null, firstNonBlank(originalText, "track delivery"), null, null, null);
            case "4", "four", "talk to shop", "connect to agent", "agent", "support" ->
                    new BotUnderstanding(BotIntent.HUMAN_HANDOFF, null, firstNonBlank(originalText, "connect to agent"), null, null, null);
            case "5", "five", "show more", "more", "browse categories", "browse category" ->
                    new BotUnderstanding(BotIntent.CATEGORY_BROWSE, null, firstNonBlank(originalText, "categories"), null, null, null);
            default -> null;
        };
    }

    private BotUnderstanding enrichProductUnderstanding(String originalText, BotUnderstanding understanding) {
        if (understanding == null || understanding.intent() != BotIntent.PRODUCT_SEARCH) {
            return understanding;
        }
        String combinedText = firstNonBlank(originalText, understanding.searchText(), "");
        String category = resolveCatalogCategory(firstNonBlank(
                understanding.category(),
                detectCategory(normalize(combinedText + " " + safe(understanding.searchText())))
        ));
        String occasion = firstNonBlank(
                understanding.occasion(),
                detectOccasion(normalize(combinedText + " " + safe(understanding.searchText())))
        );
        String searchText = buildSearchText(firstNonBlank(understanding.searchText(), originalText), category, occasion);
        return new BotUnderstanding(
                understanding.intent(),
                category,
                searchText,
                understanding.minPrice(),
                understanding.maxPrice(),
                occasion
        );
    }

    private Optional<InboundMessage> extractInboundMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            InboundMessage direct = extractDirectPayload(root);
            if (direct != null) {
                return Optional.of(direct);
            }
            InboundMessage gupshup = extractGupshupPayload(root);
            if (gupshup != null) {
                return Optional.of(gupshup);
            }
            InboundMessage meta = extractMetaPayload(root);
            if (meta != null) {
                return Optional.of(meta);
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

    private InboundMessage extractGupshupPayload(JsonNode root) {
        JsonNode payload = root.path("payload");
        if (payload.isMissingNode() || payload.isNull()) {
            return null;
        }
        String text = firstNonBlank(
                payload.path("payload").path("postbackText").asText(null),
                payload.path("postbackText").asText(null),
                payload.path("payload").path("id").asText(null),
                payload.path("payload").path("payload").asText(null),
                payload.path("payload").path("selectedRowId").asText(null),
                payload.path("payload").path("postback").path("id").asText(null),
                payload.path("payload").path("button").path("id").asText(null),
                payload.path("payload").path("button").path("payload").asText(null),
                payload.path("payload").path("title").asText(null),
                payload.path("payload").path("button").path("text").asText(null),
                payload.path("payload").path("list_reply").path("title").asText(null),
                payload.path("payload").path("list_reply").path("id").asText(null),
                payload.path("payload").path("text").asText(null),
                payload.path("id").asText(null),
                payload.path("payload").asText(null),
                payload.path("selectedRowId").asText(null),
                payload.path("title").asText(null),
                payload.path("button").path("id").asText(null),
                payload.path("button").path("payload").asText(null),
                payload.path("button").path("text").asText(null),
                payload.path("list_reply").path("title").asText(null),
                payload.path("list_reply").path("id").asText(null),
                payload.path("text").asText(null),
                payload.path("message").asText(null)
        );
        String from = firstNonBlank(
                payload.path("source").asText(null),
                payload.path("sender").path("phone").asText(null),
                payload.path("phone").asText(null)
        );
        if (!hasText(text) || !hasText(from)) {
            return null;
        }
        String name = firstNonBlank(payload.path("sender").path("name").asText(null), payload.path("name").asText(null), "Customer");
        String messageId = firstNonBlank(payload.path("id").asText(null), payload.path("messageId").asText(null), root.path("messageId").asText(null));
        return new InboundMessage(from, name, text, messageId);
    }

    private InboundMessage extractMetaPayload(JsonNode root) {
        JsonNode value = root.path("entry").path(0).path("changes").path(0).path("value");
        JsonNode message = value.path("messages").path(0);
        if (message.isMissingNode() || message.isNull()) {
            return null;
        }
        String text = firstNonBlank(
                message.path("text").path("body").asText(null),
                message.path("interactive").path("list_reply").path("title").asText(null),
                message.path("interactive").path("list_reply").path("id").asText(null),
                message.path("interactive").path("button_reply").path("id").asText(null),
                message.path("interactive").path("button_reply").path("title").asText(null),
                message.path("button").path("text").asText(null)
        );
        String from = message.path("from").asText(null);
        if (!hasText(text) || !hasText(from)) {
            return null;
        }
        String name = firstNonBlank(value.path("contacts").path(0).path("profile").path("name").asText(null), "Customer");
        return new InboundMessage(from, name, text, message.path("id").asText(null));
    }

    private SendResult sendWhatsAppText(String to, String text) {
        MarketingChannelResult result = whatsAppMessageService.sendText(to, text);
        return new SendResult(result.isSuccess(), result.getResponseId(), result.getErrorMessage());
    }

    private SendResult sendReply(String to, BotReply reply) {
        if (reply == null) {
            return sendWhatsAppText(to, buildFallbackMenuReply());
        }
        if (hasProductImage(reply.products())) {
            SendResult introResult = sendWhatsAppText(to, reply.text());
            SendResult mediaResult = sendProductImagesIfAvailable(to, reply.products());
            SendResult combined = combineSequentialResults(introResult, mediaResult);
            if (hasInteractiveReply(reply)) {
                SendResult interactiveResult = sendInteractiveReply(to, productActionPromptReply(reply));
                return combineSequentialResults(combined, interactiveResult);
            }
            return combined;
        }
        SendResult mediaResult = null;
        if (hasText(reply.mediaUrl())) {
            MarketingChannelResult result = whatsAppMessageService.sendImage(
                    to,
                    publicImageUrl(reply.mediaUrl()),
                    defaultString(reply.mediaCaption(), reply.text())
            );
            mediaResult = toSendResult(result);
            if (!hasInteractiveReply(reply)) {
                if (mediaResult.success()) {
                    return mediaResult;
                }
                SendResult textResult = sendWhatsAppText(to, reply.text());
                return combineTextFallbackWithImageFailure(textResult, mediaResult);
            }
        }
        if (hasInteractiveReply(reply)) {
            BotReply interactiveReply = mediaResult != null && mediaResult.success()
                    ? compactInteractiveReply(reply)
                    : reply;
            SendResult interactiveResult = sendInteractiveReply(to, interactiveReply);
            if (mediaResult == null && interactiveResult == null) {
                return sendWhatsAppText(to, reply.text());
            }
            return combineSequentialResults(mediaResult, interactiveResult);
        }
        return sendWhatsAppText(to, reply.text());
    }

    private boolean hasInteractiveReply(BotReply reply) {
        return reply != null
                && ((reply.sections() != null && !reply.sections().isEmpty())
                || (reply.buttons() != null && !reply.buttons().isEmpty()));
    }

    private BotReply compactInteractiveReply(BotReply reply) {
        if (reply == null) {
            return null;
        }
        String text = reply.text();
        if (hasProductImage(reply.products()) && reply.buttons() != null && !reply.buttons().isEmpty()) {
            text = "Choose what you would like to do next.";
        } else if (reply.sections() != null && !reply.sections().isEmpty()) {
            text = "Please choose an option below.";
        }
        return new BotReply(
                text,
                reply.productCount(),
                List.of(),
                null,
                null,
                reply.buttons(),
                reply.sections(),
                reply.listHeader(),
                reply.listButtonText()
        );
    }

    private BotReply productActionPromptReply(BotReply reply) {
        return new BotReply(
                "Choose an option below.",
                reply.productCount(),
                List.of(),
                null,
                null,
                reply.buttons(),
                reply.sections(),
                firstNonBlank(reply.listHeader(), "Product Actions"),
                firstNonBlank(reply.listButtonText(), "Actions")
        );
    }

    private SendResult sendInteractiveReply(String to, BotReply reply) {
        boolean hasButtons = reply.buttons() != null && !reply.buttons().isEmpty();
        boolean hasSections = reply.sections() != null && !reply.sections().isEmpty();
        if (hasButtons && hasSections) {
            MarketingChannelResult buttonsResult = whatsAppMessageService.sendReplyButtons(to, reply.text(), reply.buttons());
            MarketingChannelResult listResult = whatsAppMessageService.sendListMessage(
                    to,
                    defaultString(reply.listHeader(), "Krishnai Assistant"),
                    "More options below.",
                    defaultString(reply.listButtonText(), "Choose"),
                    reply.sections()
            );
            return combineSequentialResults(
                    toSendResult(buttonsResult),
                    toSendResult(listResult)
            );
        }
        MarketingChannelResult result;
        if (hasSections) {
            result = whatsAppMessageService.sendListMessage(
                    to,
                    defaultString(reply.listHeader(), "Krishnai Assistant"),
                    reply.text(),
                    defaultString(reply.listButtonText(), "Choose"),
                    reply.sections()
            );
        } else {
            result = whatsAppMessageService.sendReplyButtons(to, reply.text(), reply.buttons());
        }
        if (result == null) {
            return null;
        }
        return new SendResult(result.isSuccess(), result.getResponseId(), result.getErrorMessage());
    }

    private SendResult toSendResult(MarketingChannelResult result) {
        if (result == null) {
            return new SendResult(false, null, "WhatsApp sender returned no result");
        }
        return new SendResult(result.isSuccess(), result.getResponseId(), result.getErrorMessage());
    }

    private SendResult combineSequentialResults(SendResult first, SendResult second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        String messageId = firstNonBlank(joinMessageIds(first.messageId(), second.messageId()), first.messageId(), second.messageId());
        if (second.success()) {
            return new SendResult(true, messageId, first.success() ? null : "First message failed: " + safe(first.errorMessage()));
        }
        if (first.success()) {
            return new SendResult(true, messageId, "Interactive message failed: " + safe(second.errorMessage()));
        }
        return new SendResult(false, messageId, firstNonBlank(second.errorMessage(), first.errorMessage()));
    }

    private BotReply polishReplyWithOrchestrator(BotInboundMessage inbound,
                                                 BotIntentClassification classification,
                                                 BotReply reply) {
        if (reply == null || botOrchestratorService == null || hasProductImage(reply.products())) {
            return reply;
        }
        try {
            String polished = botOrchestratorService.polishReply(inbound, classification, reply.text());
            if (!hasText(polished) || polished.length() > 1800) {
                return reply;
            }
            return new BotReply(polished,
                    reply.productCount(),
                    reply.products(),
                    reply.mediaUrl(),
                    reply.mediaCaption(),
                    reply.buttons(),
                    reply.sections(),
                    reply.listHeader(),
                    reply.listButtonText());
        } catch (Exception exception) {
            log.debug("Unable to polish WhatsApp bot reply", exception);
            return reply;
        }
    }

    private boolean hasProductImage(List<OmnichannelProductCardResponse> products) {
        return products != null && products.stream().anyMatch(product -> hasText(publicImageUrl(product.getImageUrl())));
    }

    private SendResult sendProductImagesIfAvailable(String to, List<OmnichannelProductCardResponse> products) {
        if (products == null || products.isEmpty()) {
            return null;
        }
        List<OmnichannelProductCardResponse> productsWithImages = products.stream()
                .filter(product -> hasText(publicImageUrl(product.getImageUrl())))
                .limit(PRODUCT_IMAGE_SEND_LIMIT)
                .toList();
        if (productsWithImages.isEmpty()) {
            return null;
        }
        SendResult combined = null;
        int total = productsWithImages.size();
        int index = 1;
        for (OmnichannelProductCardResponse product : productsWithImages) {
            MarketingChannelResult result = whatsAppMessageService.sendImage(
                    to,
                    publicImageUrl(product.getImageUrl()),
                    productImageCaption(product, index++, total)
            );
            combined = combineSequentialResults(
                    combined,
                    new SendResult(result.isSuccess(), result.getResponseId(), result.getErrorMessage())
            );
        }
        return combined;
    }

    private SendResult combineTextFallbackWithImageFailure(SendResult textResult, SendResult imageResult) {
        if (imageResult == null || imageResult.success()) {
            return imageResult == null ? textResult : imageResult;
        }
        if (textResult.success()) {
            return new SendResult(true, textResult.messageId(), "Product image failed: " + safe(imageResult.errorMessage()));
        }
        return new SendResult(false,
                firstNonBlank(textResult.messageId(), imageResult.messageId()),
                firstNonBlank(textResult.errorMessage(), imageResult.errorMessage()));
    }

    private SendResult combineSendResults(SendResult textResult, SendResult imageResult) {
        if (imageResult == null) {
            return textResult;
        }
        String messageId = firstNonBlank(
                joinMessageIds(textResult.messageId(), imageResult.messageId()),
                textResult.messageId(),
                imageResult.messageId()
        );
        String error = textResult.success()
                ? (imageResult.success() ? null : "Product image failed: " + safe(imageResult.errorMessage()))
                : textResult.errorMessage();
        return new SendResult(textResult.success(), messageId, error);
    }

    private String joinMessageIds(String textMessageId, String imageMessageId) {
        if (!hasText(textMessageId) || !hasText(imageMessageId)) {
            return "";
        }
        return textMessageId + ";image=" + imageMessageId;
    }

    private String productImageCaption(OmnichannelProductCardResponse product, int index, int total) {
        List<String> lines = new ArrayList<>();
        lines.add(index + "/" + total + " " + defaultString(product.getName(), "Product"));
        lines.add(formatPrice(product.getPrice()) + " | " + defaultString(product.getStockLabel(), "Available"));
        lines.add(productSalesPitch(product));
        lines.add("Reply VIEW " + shortProductCode(product) + " for details or ADD " + shortProductCode(product) + " for cart.");
        String caption = String.join("\n", lines);
        return caption.length() > 1000 ? caption.substring(0, 997) + "..." : caption;
    }

    private String welcomeImageUrl() {
        String configuredGreeting = publicImageUrl("/assets/krishnai-whatsapp-greeting.png");
        if (receiptSettingsRepository == null) {
            return configuredGreeting;
        }
        try {
            return receiptSettingsRepository.findAll().stream()
                    .findFirst()
                    .map(settings -> firstNonBlank(
                            configuredGreeting,
                            publicImageUrl(settings.getHeroPrimaryImageUrl()),
                            publicImageUrl(settings.getHeroSecondaryImageUrl()),
                            publicImageUrl(settings.getLogoUrl())
                    ))
                    .filter(this::hasText)
                    .orElse(configuredGreeting);
        } catch (Exception exception) {
            log.debug("Unable to load WhatsApp welcome image", exception);
            return configuredGreeting;
        }
    }

    private String productSalesPitch(OmnichannelProductCardResponse product) {
        if (product == null) {
            return "Premium pick from the latest retail collection.";
        }
        if (hasText(product.getShortBenefit())
                && !normalize(product.getShortBenefit()).contains("live stock visibility")) {
            return product.getShortBenefit();
        }
        String category = normalize(firstNonBlank(product.getCategory(), product.getName()));
        if (category.contains("necklace") || category.contains("neckalace") || category.contains("हार")) {
            return "A graceful necklace pick for festive looks, gifting, and special occasions.";
        }
        if (category.contains("earring") || category.contains("jhumka")) {
            return "Elegant earrings that pair beautifully with ethnic and party wear.";
        }
        if (category.contains("bangle") || category.contains("bracelet")) {
            return "A polished hand-jewellery pick to complete a festive look.";
        }
        if (category.contains("cosmetic") || category.contains("makeup")) {
            return "A refined beauty pick for daily use, styling, or gifting.";
        }
        return "Premium pick from the latest retail collection.";
    }

    private String productTags(OmnichannelProductCardResponse product) {
        List<String> tags = new ArrayList<>();
        String category = displayCategoryName(product == null ? null : product.getCategory());
        if (hasText(category)) {
            tags.add(category);
        }
        String pitch = normalize(productSalesPitch(product));
        if (containsAny(pitch, "wedding", "bridal", "festive")) {
            tags.add("Wedding / Festival");
        }
        if (containsAny(pitch, "daily")) {
            tags.add("Daily wear");
        }
        if (tags.size() < 2) {
            tags.add("Gold finish");
        }
        return String.join(" • ", tags.stream().distinct().limit(3).toList());
    }

    private String discountLabel(OmnichannelProductCardResponse product) {
        return product == null || product.getPrice() == null ? "Best available price" : "Included in offer price";
    }

    private String shortProductLink(OmnichannelProductCardResponse product) {
        if (product == null || product.getProductId() == null) {
            return "https://kpskrishnai.com/products";
        }
        return sourceTrackedProductUrl(product.getProductId(), "whatsapp", null);
    }

    private String shortProductCode(OmnichannelProductCardResponse product) {
        if (product == null || product.getProductId() == null) {
            return "ITEM";
        }
        return product.getProductId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private boolean isDuplicateInbound(InboundMessage message) {
        String key = inboundDedupKey(message);
        if (!hasText(key)) {
            return false;
        }
        cleanupRecentInboundMessages();
        Instant now = Instant.now();
        Instant previous = RECENT_INBOUND_MESSAGES.putIfAbsent(key, now);
        return previous != null && previous.plus(INBOUND_DEDUP_TTL).isAfter(now);
    }

    private void cleanupRecentInboundMessages() {
        Instant cutoff = Instant.now().minus(INBOUND_DEDUP_TTL);
        RECENT_INBOUND_MESSAGES.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private String inboundDedupKey(InboundMessage message) {
        if (message == null) {
            return "";
        }
        String id = firstNonBlank(message.messageId());
        if (hasText(id)) {
            return "id:" + id;
        }
        return "body:" + digitsOnly(message.from()) + ":" + normalize(message.text());
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
        List<String> categories = availableCategories();
        for (String category : categories) {
            String normalizedCategory = normalize(category);
            if (hasText(normalizedCategory) && normalizedText.contains(normalizedCategory)) {
                return category;
            }
        }
        for (Map.Entry<String, String> alias : categoryAliases().entrySet()) {
            if (normalizedText.contains(alias.getKey()) || hasCloseToken(normalizedText, alias.getKey())) {
                return resolveCatalogCategory(alias.getValue(), categories);
            }
        }
        for (String token : meaningfulTokens(normalizedText)) {
            for (String category : categories) {
                if (categoryLooksLike(token, category)) {
                    return category;
                }
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

    private boolean isBudgetOnly(String normalizedText) {
        if (!hasText(normalizedText)) {
            return false;
        }
        String compact = normalizedText.replaceAll("\\s+", "");
        if (compact.matches("(?:rs|inr)?\\d{2,7}")) {
            return true;
        }
        return MONEY_PATTERN.matcher(normalizedText).matches()
                && !hasRecognizableCatalogSignal(normalizedText)
                && !containsAny(normalizedText, "order", "payment", "track", "otp", "phone", "mobile");
    }

    private String extractFirstAmount(String normalizedText) {
        Matcher matcher = MONEY_PATTERN.matcher(normalizedText);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return normalizedText.replaceAll("\\D", "");
    }

    private BigDecimal extractPositiveAmount(String normalizedText) {
        String amount = extractFirstAmount(normalizedText);
        if (!hasText(amount)) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(amount);
            return positiveAmountOrNull(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal positiveAmountOrNull(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0 ? amount : null;
    }

    private boolean isMoreRequest(String normalizedText) {
        return "more".equals(normalizedText)
                || "show more".equals(normalizedText)
                || "yes".equals(normalizedText)
                || "yes show more".equals(normalizedText)
                || "yes, show more".equals(normalizedText)
                || "more options".equals(normalizedText)
                || "similar".equals(normalizedText)
                || "similar items".equals(normalizedText);
    }

    private boolean isCategoryModifierOnly(String normalizedText) {
        if (!hasText(normalizedText) || hasRecognizableCatalogSignal(normalizedText)) {
            return false;
        }
        if (isBudgetOnly(normalizedText)) {
            return true;
        }
        if (MONEY_PATTERN.matcher(normalizedText).find()) {
            return true;
        }
        List<String> tokens = meaningfulTokens(normalizedText);
        if (tokens.isEmpty() || tokens.size() > 4) {
            return false;
        }
        return tokens.stream().allMatch(token ->
                COLOR_STYLE_TERMS.contains(token)
                        || containsAny(token, "daily", "party", "bridal", "wedding", "gift", "office", "simple", "premium", "cheap")
        );
    }

    private boolean hasRecognizableCatalogSignal(String normalizedText) {
        return hasText(detectCategory(normalizedText))
                || hasText(detectOccasion(normalizedText))
                || containsAny(normalizedText, "gift", "bridal", "daily wear", "party", "festival", "new arrival", "trending", "featured");
    }

    private String detectOccasion(String normalizedText) {
        if (containsAny(normalizedText, "wedding", "bridal", "लग्न", "shaadi")) {
            return "wedding";
        }
        if (containsAny(normalizedText, "gift", "birthday", "anniversary", "mother", "mothers day", "आई", "भेट")) {
            return "gifting";
        }
        if (containsAny(normalizedText, "party", "function", "festival", "diwali", "दिवाळी", "सण")) {
            return "occasion wear";
        }
        if (containsAny(normalizedText, "daily", "office", "regular", "simple", "दररोज")) {
            return "daily wear";
        }
        return null;
    }

    private String buildSearchText(String original, String category, String occasion) {
        List<String> terms = new ArrayList<>();
        if (hasText(category)) {
            addUniqueTerm(terms, category);
            addUniqueTerm(terms, canonicalCategoryTerm(category));
        }
        if (hasText(occasion)) {
            addUniqueTerm(terms, occasion);
        }
        for (String token : normalize(original).split("\\s+")) {
            if (token.length() > 2 && !STOP_WORDS.contains(token)) {
                addUniqueTerm(terms, correctSearchToken(token));
            }
        }
        return terms.isEmpty() ? original : String.join(" ", terms);
    }

    private OmnichannelProductSearchResponse fallbackProductSearch(OmnichannelProductSearchRequest request, BotUnderstanding understanding) {
        int limit = request.getLimit() == null ? 5 : Math.max(1, request.getLimit());
        List<UUID> exclude = request.getExcludeProductIds() == null ? List.of() : request.getExcludeProductIds();
        List<Product> catalog = productRepository.findAll();
        List<OmnichannelProductCardResponse> products = catalog.stream()
                .filter(product -> exclude.isEmpty() || !exclude.contains(product.getId()))
                .filter(product -> isCatalogProductAvailable(product, request))
                .map(product -> new ScoredProduct(product, scoreProduct(product, understanding)))
                .filter(scored -> scored.score() > 0)
                .sorted((left, right) -> {
                    int scoreCompare = Integer.compare(right.score(), left.score());
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    return safe(left.product().getName()).compareToIgnoreCase(safe(right.product().getName()));
                })
                .limit(limit)
                .map(scored -> mapFallbackProduct(scored.product(), request))
                .toList();
        if (products.isEmpty() && isBroadShoppingRequest(understanding)) {
            products = catalog.stream()
                    .filter(product -> exclude.isEmpty() || !exclude.contains(product.getId()))
                    .filter(product -> isCatalogProductAvailable(product, request))
                    .sorted(Comparator.comparing(Product::getQuantity, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(limit)
                    .map(product -> mapFallbackProduct(product, request))
                    .toList();
        }

        return OmnichannelProductSearchResponse.builder()
                .query(firstNonBlank(understanding.searchText(), understanding.category(), request.getQuery(), "Recommended products"))
                .totalMatches(products.size())
                .introMessage(products.isEmpty() ? "No close catalog match found." : "Here are close matches from the catalog.")
                .products(products)
                .build();
    }

    private boolean isBroadShoppingRequest(BotUnderstanding understanding) {
        if (understanding == null) {
            return false;
        }
        String text = normalize(understanding.searchText());
        return !hasText(understanding.category())
                && (containsAny(text, "featured", "trending", "new arrival", "jewellery", "cosmetics", "shop products")
                || "1".equals(text));
    }

    private boolean isCatalogProductAvailable(Product product, OmnichannelProductSearchRequest request) {
        if (product == null) {
            return false;
        }
        if (!productMatchesRequestedCategory(request.getCategory(), product.getCategory(), product.getName(), product.getSku())) {
            return false;
        }
        if (Boolean.TRUE.equals(request.getInStockOnly()) && (product.getQuantity() == null || product.getQuantity() <= 0)) {
            return false;
        }
        BigDecimal price = product.getResolvedWebsitePrice();
        if (price == null) {
            return false;
        }
        return (request.getMinPrice() == null || price.compareTo(request.getMinPrice()) >= 0)
                && (request.getMaxPrice() == null || price.compareTo(request.getMaxPrice()) <= 0);
    }

    private int scoreProduct(Product product, BotUnderstanding understanding) {
        String haystack = normalize(product.getName() + " " + product.getCategory() + " " + product.getSku());
        if (!hasText(haystack)) {
            return 0;
        }
        int score = 0;
        if (hasText(understanding.category()) && categoryLooksLike(understanding.category(), product.getCategory())) {
            score += 50;
        }
        for (String term : searchTerms(understanding)) {
            if (haystack.contains(term)) {
                score += 12;
                continue;
            }
            if (haystackHasCloseToken(haystack, term)) {
                score += 7;
            }
        }
        if (hasText(understanding.occasion())) {
            String occasion = normalize(understanding.occasion());
            if (haystack.contains(occasion)
                    || ("wedding".equals(occasion) && containsAny(haystack, "bridal", "wedding", "lagna", "shaadi"))) {
                score += 5;
            }
        }
        return score;
    }

    private OmnichannelProductCardResponse mapFallbackProduct(Product product, OmnichannelProductSearchRequest request) {
        String productUrl = sourceTrackedProductUrl(product.getId(), "whatsapp", request == null ? null : request.getCampaign());
        String buyNowUrl = productUrl + "&autoAdd=" + product.getId() + "&redirect=cart";
        return OmnichannelProductCardResponse.builder()
                .productId(product.getId())
                .name(product.getName())
                .category(product.getCategory())
                .sku(product.getSku())
                .price(product.getResolvedWebsitePrice())
                .quantity(product.getQuantity())
                .inStock(product.getQuantity() != null && product.getQuantity() > 0)
                .stockLabel(product.getQuantity() != null && product.getQuantity() > 0 ? "Available now" : "Out of stock")
                .imageUrl(publicImageUrl(product.getImageDataUrl()))
                .shortBenefit("Available in " + defaultString(product.getCategory(), "our collection"))
                .productUrl(productUrl)
                .buyNowUrl(buyNowUrl)
                .checkoutUrl(buyNowUrl.replace("redirect=cart", "redirect=checkout"))
                .build();
    }

    private String sourceTrackedProductUrl(UUID productId, String source, String campaignId) {
        if (productId == null) {
            return "https://kpskrishnai.com/products";
        }
        StringBuilder url = new StringBuilder("https://kpskrishnai.com/product/")
                .append(productId)
                .append("?source=")
                .append(hasText(source) ? source : "whatsapp");
        if (isUuid(campaignId)) {
            url.append("&campaignId=").append(campaignId.trim());
        }
        return url.toString();
    }

    private boolean isUuid(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean shouldExcludeShownProducts(boolean moreRequested, String from, String category) {
        if (!hasText(from)) {
            return false;
        }
        BotSession session = sessionFor(from);
        if (session.shownProductHistory() == null || session.shownProductHistory().isEmpty()) {
            return false;
        }
        return moreRequested || categoryLooksLike(category, session.lastCategory());
    }

    private OmnichannelProductSearchResponse withProductCards(OmnichannelProductSearchResponse response,
                                                              List<OmnichannelProductCardResponse> productCards) {
        if (response == null) {
            return OmnichannelProductSearchResponse.builder()
                    .query("Recommended products")
                    .totalMatches(productCards.size())
                    .products(productCards)
                    .build();
        }
        return OmnichannelProductSearchResponse.builder()
                .query(response.getQuery())
                .totalMatches(productCards.size())
                .introMessage(response.getIntroMessage())
                .products(productCards)
                .build();
    }

    private List<OmnichannelProductCardResponse> filterProductCardsForRequestedCategory(List<OmnichannelProductCardResponse> productCards,
                                                                                       String requestedCategory) {
        if (!hasText(requestedCategory) || productCards == null || productCards.isEmpty()) {
            return productCards == null ? List.of() : productCards;
        }
        return productCards.stream()
                .filter(card -> productMatchesRequestedCategory(requestedCategory, card.getCategory(), card.getName(), card.getSku()))
                .toList();
    }

    private boolean productMatchesRequestedCategory(String requestedCategory,
                                                    String productCategory,
                                                    String productName,
                                                    String sku) {
        if (!hasText(requestedCategory)) {
            return true;
        }
        if (productNameConflictsWithRequestedCategory(requestedCategory, productName)) {
            return false;
        }
        String haystack = normalize(productCategory + " " + productName + " " + sku);
        for (String term : categoryMatchTerms(requestedCategory)) {
            String normalizedTerm = normalize(term);
            if (!hasText(normalizedTerm)) {
                continue;
            }
            if (categoryLooksLike(normalizedTerm, productCategory)
                    || categoryLooksLike(normalizedTerm, productName)
                    || haystack.contains(normalizedTerm)) {
                return true;
            }
            for (String token : meaningfulTokens(normalizedTerm)) {
                if (haystack.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean productNameConflictsWithRequestedCategory(String requestedCategory, String productName) {
        if (!hasText(requestedCategory) || !hasText(productName)) {
            return false;
        }
        Set<String> requestedGroups = categoryMatchTerms(requestedCategory).stream()
                .map(this::categoryGroups)
                .flatMap(Set::stream)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requestedGroups.isEmpty()) {
            return false;
        }
        Set<String> productNameGroups = categoryGroups(productName);
        if (productNameGroups.isEmpty() || productNameGroups.stream().anyMatch(requestedGroups::contains)) {
            return false;
        }
        return productNameGroups.stream().anyMatch(PRODUCT_CATEGORY_CONFLICT_GROUPS::contains);
    }

    private Set<String> categoryGroups(String value) {
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        String normalized = normalize(value);
        if (!hasText(normalized)) {
            return groups;
        }
        for (Map.Entry<String, String> alias : categoryAliases().entrySet()) {
            String group = alias.getValue();
            if (!PRODUCT_CATEGORY_CONFLICT_GROUPS.contains(group)) {
                continue;
            }
            String key = normalize(alias.getKey());
            if (normalized.equals(key) || normalized.contains(key) || isCloseWord(normalized, key)) {
                groups.add(group);
            }
        }
        for (String token : meaningfulTokens(normalized)) {
            String corrected = canonicalCategoryTerm(token);
            if (PRODUCT_CATEGORY_CONFLICT_GROUPS.contains(corrected)) {
                groups.add(corrected);
            }
        }
        return groups;
    }

    private List<String> categoryMatchTerms(String requestedCategory) {
        List<String> terms = new ArrayList<>();
        addUniqueTerm(terms, requestedCategory);
        addUniqueTerm(terms, configuredCategoryDisplayName(requestedCategory));
        addUniqueTerm(terms, canonicalCategoryTerm(requestedCategory));
        return terms;
    }

    private String publicImageUrl(String imageUrl) {
        if (!hasText(imageUrl)) {
            return null;
        }
        String trimmed = imageUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("data:")) {
            return null;
        }
        if (trimmed.startsWith("/")) {
            return "https://kpskrishnai.com" + trimmed;
        }
        if (trimmed.startsWith("api/")) {
            return "https://kpskrishnai.com/" + trimmed;
        }
        return trimmed;
    }

    private Set<String> searchTerms(BotUnderstanding understanding) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        List<String> values = new ArrayList<>();
        values.add(understanding.category());
        values.add(canonicalCategoryTerm(understanding.category()));
        values.add(understanding.searchText());
        values.add(understanding.occasion());
        for (String value : values) {
            for (String token : meaningfulTokens(value)) {
                terms.add(correctSearchToken(token));
            }
        }
        return terms;
    }

    private Map<String, String> categoryAliases() {
        return Map.ofEntries(
                Map.entry("earring", "earrings"),
                Map.entry("earrings", "earrings"),
                Map.entry("earing", "earrings"),
                Map.entry("earings", "earrings"),
                Map.entry("jhumka", "earrings"),
                Map.entry("झुमका", "earrings"),
                Map.entry("necklace", "necklace"),
                Map.entry("neckalce", "necklace"),
                Map.entry("neckalace", "necklace"),
                Map.entry("neckless", "necklace"),
                Map.entry("neckles", "necklace"),
                Map.entry("nekles", "necklace"),
                Map.entry("neck piece", "necklace"),
                Map.entry("neckpiece", "necklace"),
                Map.entry("chain", "necklace"),
                Map.entry("mala", "necklace"),
                Map.entry("maal", "necklace"),
                Map.entry("haar", "necklace"),
                Map.entry("har", "necklace"),
                Map.entry("set", "necklace"),
                Map.entry("हार", "necklace"),
                Map.entry("माळ", "necklace"),
                Map.entry("माला", "necklace"),
                Map.entry("bangle", "bangles"),
                Map.entry("bangles", "bangles"),
                Map.entry("bangels", "bangles"),
                Map.entry("bangal", "bangles"),
                Map.entry("chudi", "bangles"),
                Map.entry("chuda", "bangles"),
                Map.entry("kangan", "bangles"),
                Map.entry("बांगडी", "bangles"),
                Map.entry("बांगड्या", "bangles"),
                Map.entry("bracelet", "bracelet"),
                Map.entry("bracelete", "bracelet"),
                Map.entry("cosmetic", "cosmetics"),
                Map.entry("cosmetics", "cosmetics"),
                Map.entry("cosmatic", "cosmetics"),
                Map.entry("cosmatics", "cosmetics"),
                Map.entry("makeup", "cosmetics"),
                Map.entry("lipstick", "cosmetics"),
                Map.entry("lipstic", "cosmetics"),
                Map.entry("kajal", "cosmetics"),
                Map.entry("eyeliner", "cosmetics"),
                Map.entry("liner", "cosmetics"),
                Map.entry("foundation", "cosmetics"),
                Map.entry("powder", "cosmetics"),
                Map.entry("toner", "cosmetics"),
                Map.entry("facewash", "cosmetics"),
                Map.entry("face wash", "cosmetics"),
                Map.entry("perfume", "cosmetics"),
                Map.entry("highlighter", "cosmetics"),
                Map.entry("gift", "gifts"),
                Map.entry("gifting", "gifts"),
                Map.entry("bridal", "bridal"),
                Map.entry("bride", "bridal"),
                Map.entry("mangalsutra", "mangalsutra"),
                Map.entry("mangal sutra", "mangalsutra"),
                Map.entry("nose pin", "nose pin"),
                Map.entry("nosepin", "nose pin"),
                Map.entry("nath", "nose pin"),
                Map.entry("pendant", "pendant"),
                Map.entry("ring", "rings"),
                Map.entry("rings", "rings")
        );
    }

    private String resolveCatalogCategory(String candidate) {
        return resolveCatalogCategory(candidate, availableCategories());
    }

    private String resolveCatalogCategory(String candidate, List<String> categories) {
        if (!hasText(candidate)) {
            return null;
        }
        String normalizedCandidate = normalize(candidate);
        for (String category : categories) {
            if (normalize(category).equals(normalizedCandidate)) {
                return category;
            }
        }
        for (String category : categories) {
            String normalizedCategory = normalize(category);
            if (hasText(normalizedCategory) && (normalizedCategory.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedCategory))) {
                return category;
            }
        }
        for (String category : categories) {
            if (categoryLooksLike(normalizedCandidate, category)) {
                return category;
            }
        }
        return candidate;
    }

    private String canonicalCategoryTerm(String category) {
        if (!hasText(category)) {
            return null;
        }
        String normalized = normalize(category);
        for (Map.Entry<String, String> alias : categoryAliases().entrySet()) {
            if (normalized.equals(alias.getKey()) || normalized.equals(alias.getValue()) || categoryLooksLike(normalized, alias.getValue())) {
                return alias.getValue();
            }
        }
        return category;
    }

    private String correctSearchToken(String token) {
        String normalized = normalize(token);
        if (!hasText(normalized)) {
            return token;
        }
        for (Map.Entry<String, String> alias : categoryAliases().entrySet()) {
            if (normalized.equals(alias.getKey()) || isCloseWord(normalized, alias.getKey())) {
                return alias.getValue();
            }
        }
        return normalized;
    }

    private boolean categoryLooksLike(String value, String category) {
        String normalizedValue = normalize(value);
        String normalizedCategory = normalize(category);
        if (!hasText(normalizedValue) || !hasText(normalizedCategory)) {
            return false;
        }
        if (normalizedCategory.contains(normalizedValue) || normalizedValue.contains(normalizedCategory)) {
            return true;
        }
        for (String categoryToken : meaningfulTokens(normalizedCategory)) {
            if (isCloseWord(normalizedValue, categoryToken)) {
                return true;
            }
        }
        for (String valueToken : meaningfulTokens(normalizedValue)) {
            if (isCloseWord(valueToken, normalizedCategory)) {
                return true;
            }
            for (String categoryToken : meaningfulTokens(normalizedCategory)) {
                if (isCloseWord(valueToken, categoryToken)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasCloseToken(String text, String target) {
        for (String token : meaningfulTokens(text)) {
            if (isCloseWord(token, target)) {
                return true;
            }
        }
        return false;
    }

    private boolean haystackHasCloseToken(String haystack, String target) {
        for (String token : meaningfulTokens(haystack)) {
            if (isCloseWord(token, target)) {
                return true;
            }
        }
        return false;
    }

    private List<String> meaningfulTokens(String text) {
        String normalized = normalize(text);
        if (!hasText(normalized)) {
            return List.of();
        }
        return java.util.Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(token -> token.length() > 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .toList();
    }

    private void addUniqueTerm(List<String> terms, String value) {
        String normalized = normalize(value);
        if (!hasText(normalized)) {
            return;
        }
        boolean exists = terms.stream().map(this::normalize).anyMatch(normalized::equals);
        if (!exists) {
            terms.add(value.trim());
        }
    }

    private boolean isCloseWord(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (!hasText(normalizedLeft) || !hasText(normalizedRight)) {
            return false;
        }
        int shorterLength = Math.min(normalizedLeft.length(), normalizedRight.length());
        if (normalizedLeft.equals(normalizedRight)
                || (shorterLength >= 5 && (normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft)))) {
            return true;
        }
        int maxLength = Math.max(normalizedLeft.length(), normalizedRight.length());
        if (maxLength < 4) {
            return false;
        }
        int allowedDistance = maxLength <= 5 ? 1 : Math.min(3, Math.max(1, maxLength / 4));
        return levenshteinDistance(normalizedLeft, normalizedRight, allowedDistance) <= allowedDistance;
    }

    private int levenshteinDistance(String left, String right, int stopAfter) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            int rowMinimum = current[0];
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                rowMinimum = Math.min(rowMinimum, current[j]);
            }
            if (rowMinimum > stopAfter) {
                return stopAfter + 1;
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }

    private List<String> availableCategories() {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        if (productCategoryOptionRepository != null) {
            try {
                productCategoryOptionRepository.findByActiveTrueOrderByDisplayNameAsc().stream()
                        .map(option -> firstNonBlank(option.getCode(), option.getDisplayName()))
                        .filter(this::hasText)
                        .forEach(categories::add);
            } catch (Exception exception) {
                log.debug("Unable to load configured WhatsApp categories", exception);
            }
        }
        productRepository.findAll().stream()
                .sorted(Comparator.comparing(Product::getCategory, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(Product::getCategory)
                .filter(this::hasText)
                .forEach(categories::add);
        return new ArrayList<>(categories);
    }

    private String configuredCategoryDisplayName(String category) {
        if (productCategoryOptionRepository == null || !hasText(category)) {
            return null;
        }
        String normalized = normalize(category);
        try {
            return productCategoryOptionRepository.findByActiveTrueOrderByDisplayNameAsc().stream()
                    .filter(option -> normalize(option.getCode()).equals(normalized)
                            || normalize(option.getDisplayName()).equals(normalized))
                    .map(ProductCategoryOption::getDisplayName)
                    .filter(this::hasText)
                    .findFirst()
                    .orElse(null);
        } catch (Exception exception) {
            log.debug("Unable to resolve configured WhatsApp category label", exception);
            return null;
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

    private boolean isGuidedMenuRequest(String normalizedText) {
        String value = normalize(normalizedText);
        return "help".equals(value)
                || "need help".equals(value)
                || "menu".equals(value)
                || "show products".equals(value)
                || "products".equals(value)
                || "shop".equals(value)
                || "shopping".equals(value)
                || "order".equals(value)
                || "payment".equals(value);
    }

    private boolean isShowMoreSelection(String text) {
        String value = normalize(text);
        return "5".equals(value)
                || "five".equals(value)
                || "show more".equals(value)
                || "more".equals(value);
    }

    private boolean isCategoryBrowseRequest(String normalizedText) {
        String value = normalize(normalizedText);
        return containsAny(value, "category", "categories", "browse categories", "browse collection", "collections", "what do you have", "कॅटेगरी");
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

    private String formatIndianDestination(String value) {
        String digits = digitsOnly(value);
        if (digits.length() == 10) {
            return "91" + digits;
        }
        return digits;
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

    private record BotReply(String text,
                            int productCount,
                            List<OmnichannelProductCardResponse> products,
                            String mediaUrl,
                            String mediaCaption,
                            List<WhatsAppInteractiveOption> buttons,
                            List<WhatsAppInteractiveSection> sections,
                            String listHeader,
                            String listButtonText) {
        private BotReply(String text, int productCount, List<OmnichannelProductCardResponse> products) {
            this(text, productCount, products, null, null, List.of(), List.of(), null, null);
        }

        private BotReply(String text,
                         int productCount,
                         List<OmnichannelProductCardResponse> products,
                         String mediaUrl,
                         String mediaCaption) {
            this(text, productCount, products, mediaUrl, mediaCaption, List.of(), List.of(), null, null);
        }
    }

    private record PriceRange(BigDecimal min, BigDecimal max) {
    }

    private record ScoredProduct(Product product, int score) {
    }

    private record SendResult(boolean success, String messageId, String errorMessage) {
    }

    private record BotSession(String lastCategory,
                              BigDecimal lastMaxPrice,
                              BigDecimal lastMinPrice,
                              String lastIntent,
                              String lastProductId,
                              List<UUID> shownProductHistory,
                              Instant updatedAt) {
    }

    private enum BotIntent {
        PRODUCT_SEARCH,
        CATEGORY_BROWSE,
        GREETING,
        ORDER_SUPPORT,
        PAYMENT_SUPPORT,
        OFFER_INQUIRY,
        HUMAN_HANDOFF,
        THANKS,
        UNKNOWN
    }
}
