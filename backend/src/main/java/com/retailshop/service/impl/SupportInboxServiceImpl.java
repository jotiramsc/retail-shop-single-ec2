package com.retailshop.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.dto.SupportConversationDetailResponse;
import com.retailshop.dto.SupportConversationMessageResponse;
import com.retailshop.dto.SupportConversationSummaryResponse;
import com.retailshop.dto.SupportInboxSummaryResponse;
import com.retailshop.entity.OmnichannelConversation;
import com.retailshop.entity.OmnichannelConversationMessage;
import com.retailshop.entity.OmnichannelLead;
import com.retailshop.entity.Offer;
import com.retailshop.entity.Product;
import com.retailshop.enums.DiscountType;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.OfferRepository;
import com.retailshop.repository.OmnichannelConversationMessageRepository;
import com.retailshop.repository.OmnichannelConversationRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.SupportInboxService;
import com.retailshop.service.WhatsAppMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportInboxServiceImpl implements SupportInboxService {

    private static final List<String> ACTIVE_STATUSES = List.of("OPEN", "IN_PROGRESS");

    private final OmnichannelConversationRepository conversationRepository;
    private final OmnichannelConversationMessageRepository messageRepository;
    private final ProductRepository productRepository;
    private final OfferRepository offerRepository;
    private final WhatsAppMessageService whatsAppMessageService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public SupportInboxSummaryResponse getSummary() {
        List<OmnichannelConversation> conversations = conversationRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"));
        long openCount = conversations.stream()
                .filter(this::isWhatsapp)
                .filter(conversation -> ACTIVE_STATUSES.contains(safe(conversation.getStatus())))
                .count();
        long unreadCount = conversations.stream()
                .filter(this::isWhatsapp)
                .mapToLong(this::unreadCount)
                .sum();
        return SupportInboxSummaryResponse.builder()
                .openCount(openCount)
                .unreadCount(unreadCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportConversationSummaryResponse> listConversations(String status, String search, LocalDate fromDate, LocalDate toDate) {
        String normalizedStatus = safe(status);
        String normalizedSearch = normalize(search);
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.plusDays(1).atStartOfDay();
        return conversationRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .filter(this::isWhatsapp)
                .filter(conversation -> !hasText(normalizedStatus)
                        || ("ACTIVE".equals(normalizedStatus) && ACTIVE_STATUSES.contains(safe(conversation.getStatus())))
                        || normalizedStatus.equals(safe(conversation.getStatus())))
                .filter(conversation -> !hasText(normalizedSearch) || searchMatches(conversation, normalizedSearch))
                .filter(conversation -> from == null || !dateForFilter(conversation).isBefore(from))
                .filter(conversation -> to == null || dateForFilter(conversation).isBefore(to))
                .limit(100)
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SupportConversationDetailResponse getConversation(UUID conversationId) {
        return toDetail(findConversation(conversationId));
    }

    @Override
    @Transactional
    public SupportConversationDetailResponse reply(UUID conversationId, String message) {
        if (!hasText(message)) {
            throw new BusinessException("Reply message is required");
        }
        OmnichannelConversation conversation = findConversation(conversationId);
        String to = customerPhone(conversation);
        MarketingChannelResult result = whatsAppMessageService.sendText(to, message.trim());
        saveOutbound(conversation, message.trim(), "TEXT", result);
        conversation.setStatus("IN_PROGRESS");
        conversationRepository.save(conversation);
        return toDetail(conversation);
    }

    @Override
    @Transactional
    public SupportConversationDetailResponse sendProduct(UUID conversationId, UUID productId) {
        OmnichannelConversation conversation = findConversation(conversationId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("Product not found"));
        if (!Boolean.TRUE.equals(product.getShowOnWebsite())) {
            throw new BusinessException("Only products visible on the website can be sent to WhatsApp");
        }
        String productUrl = "https://kpskrishnai.com/product/" + product.getId();
        String addToCartUrl = "https://kpskrishnai.com/cart/add?productId=" + product.getId() + "&source=whatsapp-support";
        String to = customerPhone(conversation);
        String message = "Suggested by Krishnai support team\n\n"
                + product.getName() + "\n"
                + formatProductPrices(product) + "\n"
                + "Stock: " + stockLabel(product) + "\n"
                + "Category: " + defaultString(product.getCategory(), "Krishnai collection") + "\n"
                + "View Product: " + productUrl + "\n"
                + "Add to Cart: " + addToCartUrl;
        String imageUrl = publicImageUrl(product.getImageDataUrl());
        MarketingChannelResult result = hasText(imageUrl)
                ? whatsAppMessageService.sendImage(to, imageUrl, message)
                : whatsAppMessageService.sendText(to, message);
        saveProductOutbound(conversation, message, product, to, result);
        conversation.setStatus("IN_PROGRESS");
        conversationRepository.save(conversation);
        return toDetail(conversation);
    }

    @Override
    @Transactional
    public SupportConversationDetailResponse markResolved(UUID conversationId) {
        OmnichannelConversation conversation = findConversation(conversationId);
        conversation.setStatus("RESOLVED");
        conversation.setResolvedAt(LocalDateTime.now());
        conversation.setResolvedBy(currentAgentName());
        conversationRepository.save(conversation);
        return toDetail(conversation);
    }

    @Override
    @Transactional
    public SupportConversationDetailResponse reopen(UUID conversationId) {
        OmnichannelConversation conversation = findConversation(conversationId);
        conversation.setStatus("OPEN");
        conversation.setResolvedAt(null);
        conversation.setResolvedBy(null);
        conversationRepository.save(conversation);
        return toDetail(conversation);
    }

    private OmnichannelConversation findConversation(UUID conversationId) {
        OmnichannelConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException("Support conversation not found"));
        if (!isWhatsapp(conversation)) {
            throw new BusinessException("Only WhatsApp support conversations are supported");
        }
        return conversation;
    }

    private void saveOutbound(OmnichannelConversation conversation, String message, String type, MarketingChannelResult result) {
        OmnichannelConversationMessage outbound = new OmnichannelConversationMessage();
        outbound.setConversation(conversation);
        outbound.setDirection("OUTBOUND");
        outbound.setMessageType(type);
        outbound.setMessageText(message);
        outbound.setRawPayload("sent=" + (result != null && result.isSuccess()) + ";messageId=" + safe(result == null ? null : result.getResponseId()) + ";error=" + safe(result == null ? null : result.getErrorMessage()));
        messageRepository.save(outbound);
    }

    private SupportConversationSummaryResponse toSummary(OmnichannelConversation conversation) {
        OmnichannelLead lead = conversation.getLead();
        return SupportConversationSummaryResponse.builder()
                .id(conversation.getId())
                .customerName(defaultString(lead == null ? null : lead.getCustomerName(), "WhatsApp customer"))
                .phone(customerPhone(conversation))
                .status(conversation.getStatus())
                .latestMessage(lead == null ? null : lead.getLatestMessage())
                .unreadCount(unreadCount(conversation))
                .updatedAt(conversation.getUpdatedAt())
                .resolvedAt(conversation.getResolvedAt())
                .resolvedBy(conversation.getResolvedBy())
                .build();
    }

    private SupportConversationDetailResponse toDetail(OmnichannelConversation conversation) {
        OmnichannelLead lead = conversation.getLead();
        return SupportConversationDetailResponse.builder()
                .id(conversation.getId())
                .customerName(defaultString(lead == null ? null : lead.getCustomerName(), "WhatsApp customer"))
                .phone(customerPhone(conversation))
                .status(conversation.getStatus())
                .unreadCount(unreadCount(conversation))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .resolvedAt(conversation.getResolvedAt())
                .resolvedBy(conversation.getResolvedBy())
                .messages(messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversation.getId()).stream()
                        .map(this::toMessage)
                        .toList())
                .build();
    }

    private SupportConversationMessageResponse toMessage(OmnichannelConversationMessage message) {
        Map<String, String> metadata = parseRawPayload(message.getRawPayload());
        return SupportConversationMessageResponse.builder()
                .id(message.getId())
                .direction(message.getDirection())
                .messageType(message.getMessageType())
                .messageText(message.getMessageText())
                .productId(metadata.get("productId"))
                .productName(metadata.get("productName"))
                .sentBy(metadata.get("sentBy"))
                .customerMobile(metadata.get("customerMobile"))
                .whatsAppStatus(metadata.get("whatsAppStatus"))
                .createdAt(message.getCreatedAt())
                .build();
    }

    private void saveProductOutbound(OmnichannelConversation conversation,
                                     String message,
                                     Product product,
                                     String customerMobile,
                                     MarketingChannelResult result) {
        OmnichannelConversationMessage outbound = new OmnichannelConversationMessage();
        outbound.setConversation(conversation);
        outbound.setDirection("OUTBOUND");
        outbound.setMessageType("PRODUCT");
        outbound.setMessageText(message);
        outbound.setRawPayload(toRawPayload(Map.of(
                "productId", product.getId().toString(),
                "productName", defaultString(product.getName(), "Product"),
                "sentBy", currentAgentName(),
                "timestamp", LocalDateTime.now().toString(),
                "customerMobile", safe(customerMobile),
                "whatsAppStatus", result != null && result.isSuccess() ? "SENT" : "FAILED",
                "providerMessageId", safe(result == null ? null : result.getResponseId()),
                "providerError", safe(result == null ? null : result.getErrorMessage())
        )));
        messageRepository.save(outbound);
    }

    private long unreadCount(OmnichannelConversation conversation) {
        return messageRepository.findFirstByConversation_IdAndDirectionOrderByCreatedAtDesc(conversation.getId(), "OUTBOUND")
                .map(lastOutbound -> messageRepository.countByConversation_IdAndDirectionAndCreatedAtAfter(conversation.getId(), "INBOUND", lastOutbound.getCreatedAt()))
                .orElseGet(() -> messageRepository.countByConversation_IdAndDirection(conversation.getId(), "INBOUND"));
    }

    private boolean searchMatches(OmnichannelConversation conversation, String normalizedSearch) {
        OmnichannelLead lead = conversation.getLead();
        return normalize(customerPhone(conversation)).contains(normalizedSearch)
                || normalize(lead == null ? null : lead.getCustomerName()).contains(normalizedSearch)
                || normalize(lead == null ? null : lead.getLatestMessage()).contains(normalizedSearch);
    }

    private LocalDateTime dateForFilter(OmnichannelConversation conversation) {
        if ("RESOLVED".equals(safe(conversation.getStatus())) && conversation.getResolvedAt() != null) {
            return conversation.getResolvedAt();
        }
        return conversation.getUpdatedAt() == null ? conversation.getCreatedAt() : conversation.getUpdatedAt();
    }

    private boolean isWhatsapp(OmnichannelConversation conversation) {
        return "WHATSAPP".equalsIgnoreCase(safe(conversation.getChannel()));
    }

    private String customerPhone(OmnichannelConversation conversation) {
        OmnichannelLead lead = conversation.getLead();
        return firstNonBlank(lead == null ? null : lead.getMobile(), lead == null ? null : lead.getExternalUserId(), conversation.getExternalThreadId());
    }

    private String publicImageUrl(String imageUrl) {
        if (!hasText(imageUrl) || imageUrl.trim().startsWith("data:")) {
            return null;
        }
        String trimmed = imageUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("/")) {
            return "https://kpskrishnai.com" + trimmed;
        }
        return trimmed.startsWith("api/") ? "https://kpskrishnai.com/" + trimmed : trimmed;
    }

    private String formatProductPrices(Product product) {
        DealDisplay deal = dealDisplay(product);
        if (deal.youSave().compareTo(BigDecimal.ZERO) > 0) {
            StringBuilder builder = new StringBuilder()
                    .append("MRP: ").append(formatPrice(deal.originalPrice()))
                    .append(" | Deal: ").append(formatPrice(deal.offerPrice()))
                    .append(" | ").append(deal.discountPercent().stripTrailingZeros().toPlainString()).append("% OFF")
                    .append(" | You Save ").append(formatPrice(deal.youSave()));
            if (hasText(deal.couponCode())) {
                builder.append("\nCoupon: ").append(deal.couponCode());
            } else if (hasText(deal.offerName())) {
                builder.append("\nOffer: ").append(deal.offerName());
            }
            return builder.toString();
        }
        return "Price: " + formatPrice(deal.offerPrice());
    }

    private DealDisplay dealDisplay(Product product) {
        BigDecimal originalPrice = money(product.getResolvedWebsitePrice());
        Offer bestOffer = bestDisplayOffer(product, originalPrice);
        BigDecimal discount = bestOffer == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : discountFor(bestOffer, originalPrice);
        BigDecimal offerPrice = originalPrice.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountPercent = originalPrice.compareTo(BigDecimal.ZERO) > 0 && discount.compareTo(BigDecimal.ZERO) > 0
                ? discount.multiply(BigDecimal.valueOf(100)).divide(originalPrice, 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
        return new DealDisplay(
                originalPrice,
                offerPrice,
                discountPercent,
                discount,
                bestOffer == null ? null : bestOffer.getName(),
                bestOffer == null ? null : bestOffer.getCouponCode()
        );
    }

    private Offer bestDisplayOffer(Product product, BigDecimal basePrice) {
        if (product == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        LocalDate today = LocalDate.now();
        return offerRepository.findActiveOffers(today).stream()
                .filter(offer -> appliesTo(offer, product))
                .filter(offer -> offer.getMinOrderValue() == null || basePrice.compareTo(offer.getMinOrderValue()) >= 0)
                .max(Comparator.comparing(offer -> discountFor(offer, basePrice)))
                .orElse(null);
    }

    private boolean appliesTo(Offer offer, Product product) {
        if (offer == null || product == null) {
            return false;
        }
        if (offer.getProduct() != null) {
            return offer.getProduct().getId().equals(product.getId());
        }
        if (hasText(offer.getCategory())) {
            return offer.getCategory().equalsIgnoreCase(product.getCategory());
        }
        return true;
    }

    private BigDecimal discountFor(Offer offer, BigDecimal basePrice) {
        DiscountType discountType = offer.getDiscountType();
        BigDecimal value = offer.getDiscountValue();
        if (discountType == null || value == null) {
            discountType = switch (offer.getType()) {
                case FLAT -> DiscountType.FLAT;
                case PERCENT, CATEGORY -> DiscountType.PERCENT;
                case BUY_ONE_GET_ONE, BUY_X_GET_Y, COMBO -> null;
            };
            if (discountType == null) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            value = offer.getValue();
        }
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal discount = discountType == DiscountType.PERCENT
                ? basePrice.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : value;
        if (discountType == DiscountType.PERCENT && offer.getMaxDiscountAmount() != null) {
            discount = discount.min(offer.getMaxDiscountAmount());
        }
        return discount.min(basePrice).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String stockLabel(Product product) {
        return product.getQuantity() != null && product.getQuantity() > 0 ? "Available now" : "Out of stock";
    }

    private String currentAgentName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !hasText(authentication.getName()) ? "support-agent" : authentication.getName();
    }

    private String toRawPayload(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception exception) {
            return "whatsAppStatus=" + safe(metadata.get("whatsAppStatus")) + ";productId=" + safe(metadata.get("productId"));
        }
    }

    private Map<String, String> parseRawPayload(String rawPayload) {
        if (!hasText(rawPayload) || !rawPayload.trim().startsWith("{")) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawPayload, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "Price on request" : "₹" + price.stripTrailingZeros().toPlainString();
    }

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
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

    private String defaultString(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record DealDisplay(BigDecimal originalPrice,
                               BigDecimal offerPrice,
                               BigDecimal discountPercent,
                               BigDecimal youSave,
                               String offerName,
                               String couponCode) {
    }
}
