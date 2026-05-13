package com.retailshop.service.bot.impl;

import com.retailshop.dto.OmnichannelProductCardResponse;
import com.retailshop.dto.bot.BotDeliveryTimeline;
import com.retailshop.dto.bot.BotOrderCard;
import com.retailshop.dto.bot.BotPaymentSummary;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.OrderItem;
import com.retailshop.entity.Product;
import com.retailshop.enums.OrderStatus;
import com.retailshop.service.bot.BotResponseComposerService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Service
public class BotResponseComposerServiceImpl implements BotResponseComposerService {

    @Override
    public String productIntro(List<OmnichannelProductCardResponse> products) {
        int count = products == null ? 0 : products.size();
        if (count <= 0) {
            return "I could not find an exact match yet. Send a category, budget, or occasion and I will narrow it down.";
        }
        return "I found " + count + " close match" + (count == 1 ? "" : "es") + ". I am sharing the best product photo with details now.";
    }

    @Override
    public String productCaption(OmnichannelProductCardResponse product) {
        if (product == null) {
            return "Open our store: https://kpskrishnai.com/products";
        }
        return defaultString(product.getName(), "Product") + "\n"
                + formatPrice(product.getPrice()) + " | " + defaultString(product.getStockLabel(), "Availability shown in store") + "\n"
                + defaultString(product.getShortBenefit(), "Premium pick from the latest collection.") + "\n"
                + "View: " + shortProductLink(product) + "\n"
                + "Reply with budget/category for more options.";
    }

    @Override
    public BotOrderCard orderCard(CustomerOrder order) {
        return BotOrderCard.builder()
                .orderId(order == null || order.getId() == null ? null : order.getId().toString())
                .orderNumber(order == null ? null : order.getOrderNumber())
                .createdAt(order == null ? null : order.getCreatedAt())
                .finalAmount(order == null ? null : order.getFinalAmount())
                .paymentStatus(order == null ? null : order.getPaymentStatus())
                .orderStatus(order == null || order.getStatus() == null ? null : order.getStatus().name())
                .itemImages(itemImages(order))
                .itemCount(order == null || order.getItems() == null ? 0 : order.getItems().size())
                .actions(List.of("Track Order", "Show Items", "Reorder", "Invoice", "Connect to Agent"))
                .build();
    }

    @Override
    public BotDeliveryTimeline deliveryTimeline(CustomerOrder order) {
        return BotDeliveryTimeline.builder()
                .orderNumber(order == null ? null : order.getOrderNumber())
                .currentStage(currentStage(order == null ? null : order.getStatus()))
                .stages(List.of("Order Placed", "Confirmed", "Packed", "Shipped", "Out for Delivery", "Delivered"))
                .etaText("Delivery ETA is shown when courier details are available.")
                .supportAction("Connect to Agent")
                .build();
    }

    @Override
    public BotPaymentSummary paymentSummary(CustomerOrder order) {
        return BotPaymentSummary.builder()
                .orderNumber(order == null ? null : order.getOrderNumber())
                .paymentMethod(order == null ? null : order.getPaymentGateway())
                .paymentStatus(order == null ? null : order.getPaymentStatus())
                .amount(order == null ? null : order.getFinalAmount())
                .transactionId(order == null ? null : order.getPaymentId())
                .nextAction("Track Order or Connect to Agent")
                .build();
    }

    @Override
    public OmnichannelProductCardResponse productCard(Product product) {
        if (product == null) {
            return null;
        }
        BigDecimal price = product.getResolvedWebsitePrice();
        return OmnichannelProductCardResponse.builder()
                .productId(product.getId())
                .name(product.getName())
                .category(product.getCategory())
                .sku(product.getSku())
                .price(price)
                .quantity(product.getQuantity())
                .inStock(product.getQuantity() != null && product.getQuantity() > 0)
                .stockLabel(product.getQuantity() != null && product.getQuantity() > 0 ? "Available now" : "Out of stock")
                .imageUrl(publicImageUrl(product.getImageDataUrl()))
                .shortBenefit("Available in " + defaultString(product.getCategory(), "our collection"))
                .productUrl("https://kpskrishnai.com/products?productId=" + product.getId())
                .buyNowUrl("https://kpskrishnai.com/products?productId=" + product.getId())
                .checkoutUrl("https://kpskrishnai.com/products?productId=" + product.getId())
                .build();
    }

    private List<String> itemImages(CustomerOrder order) {
        if (order == null || order.getItems() == null) {
            return List.of();
        }
        return order.getItems().stream()
                .map(OrderItem::getProduct)
                .filter(java.util.Objects::nonNull)
                .map(Product::getImageDataUrl)
                .map(this::publicImageUrl)
                .filter(this::hasText)
                .limit(4)
                .toList();
    }

    private String currentStage(OrderStatus status) {
        if (status == null) {
            return "Order Placed";
        }
        return switch (status) {
            case PENDING -> "Order Placed";
            case CONFIRMED -> "Confirmed";
            case SHIPPED -> "Shipped";
            case DELIVERED, COMPLETED -> "Delivered";
            case CANCELLED -> "Cancelled";
            case RETURNED -> "Returned";
            case REFUND_INITIATED -> "Refund Initiated";
            case PAYMENT_FAILED -> "Payment Failed";
        };
    }

    private String shortProductLink(OmnichannelProductCardResponse product) {
        return product.getProductId() == null
                ? "https://kpskrishnai.com/products"
                : "https://kpskrishnai.com/products?productId=" + product.getProductId();
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

    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "Price available on request";
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setMaximumFractionDigits(0);
        return format.format(price);
    }

    private String defaultString(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
