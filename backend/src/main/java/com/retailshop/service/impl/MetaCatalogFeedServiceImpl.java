package com.retailshop.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.dto.FacebookFeedPreviewItemResponse;
import com.retailshop.dto.FacebookFeedPreviewResponse;
import com.retailshop.dto.FacebookFeedTokenResponse;
import com.retailshop.entity.Offer;
import com.retailshop.entity.Product;
import com.retailshop.entity.ProductCategoryOption;
import com.retailshop.entity.ReceiptSettings;
import com.retailshop.enums.DiscountType;
import com.retailshop.repository.OfferRepository;
import com.retailshop.repository.ProductCategoryOptionRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.repository.ReceiptSettingsRepository;
import com.retailshop.service.MetaCatalogFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetaCatalogFeedServiceImpl implements MetaCatalogFeedService {

    private static final Duration FEED_CACHE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ReceiptSettingsRepository receiptSettingsRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryOptionRepository productCategoryOptionRepository;
    private final OfferRepository offerRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.public-base-url:https://kpskrishnai.com}")
    private String publicBaseUrl;

    private volatile CachedFeed cachedXml;
    private volatile CachedFeed cachedCsv;

    @Override
    @Transactional
    public FacebookFeedTokenResponse generateFeedToken() {
        ReceiptSettings settings = settingsOrCreate();
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        settings.setFacebookFeedToken(token);
        receiptSettingsRepository.save(settings);
        clearCache();
        return FacebookFeedTokenResponse.builder().token(token).build();
    }

    @Override
    @Transactional(readOnly = true)
    public FacebookFeedPreviewResponse previewFeed() {
        ReceiptSettings settings = settingsOrNull();
        Map<String, ProductCategoryOption> categories = syncedCategoriesByCode();
        List<FeedProduct> products = allFeedProducts(settings, categories);
        long readyCount = products.stream().filter(product -> product.issue() == null).count();

        return FacebookFeedPreviewResponse.builder()
                .facebookCatalogEnabled(settings != null && Boolean.TRUE.equals(settings.getFacebookCatalogEnabled()))
                .pixelConfigured(settings != null && !isBlank(settings.getMetaPixelId()))
                .syncedCategories(categories.size())
                .syncedProducts((int) readyCount)
                .lastFeedGeneratedAt(settings != null ? settings.getFacebookFeedLastGeneratedAt() : null)
                .items(products.stream()
                        .limit(20)
                        .map(product -> FacebookFeedPreviewItemResponse.builder()
                                .productId(product.product().getSku())
                                .productName(product.product().getName())
                                .category(displayCategory(product.category()))
                                .price(product.price())
                                .salePrice(product.salePrice())
                                .status(product.issue() == null ? "Ready" : "Skipped")
                                .issue(product.issue())
                                .build())
                        .toList())
                .build();
    }

    @Override
    @Transactional
    public String xmlFeed(String token) {
        validatePublicAccess(token);
        CachedFeed existing = cachedXml;
        if (existing != null && !existing.expired()) {
            return existing.content();
        }
        String content = buildXmlFeed();
        cachedXml = new CachedFeed(content, Instant.now().plus(FEED_CACHE_TTL));
        touchLastGeneratedAt();
        return content;
    }

    @Override
    @Transactional
    public String csvFeed(String token) {
        validatePublicAccess(token);
        CachedFeed existing = cachedCsv;
        if (existing != null && !existing.expired()) {
            return existing.content();
        }
        String content = buildCsvFeed();
        cachedCsv = new CachedFeed(content, Instant.now().plus(FEED_CACHE_TTL));
        touchLastGeneratedAt();
        return content;
    }

    private String buildXmlFeed() {
        List<FeedProduct> products = readyFeedProducts();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\" xmlns:g=\"http://base.google.com/ns/1.0\">\n");
        xml.append("  <channel>\n");
        xml.append("    <title>").append(xml("KRISHNAI Pearl Shopee Catalog")).append("</title>\n");
        xml.append("    <link>").append(xml(baseUrl())).append("</link>\n");
        xml.append("    <description>").append(xml("Dynamic product catalog feed")).append("</description>\n");
        for (FeedProduct product : products) {
            xml.append("    <item>\n");
            xml.append("      <g:id>").append(xml(feedId(product.product()))).append("</g:id>\n");
            xml.append("      <g:title>").append(xml(product.product().getName())).append("</g:title>\n");
            xml.append("      <g:description>").append(xml(description(product.product()))).append("</g:description>\n");
            xml.append("      <g:availability>in stock</g:availability>\n");
            xml.append("      <g:condition>new</g:condition>\n");
            xml.append("      <g:price>").append(xml(moneyInr(product.price()))).append("</g:price>\n");
            if (product.salePrice() != null && product.salePrice().compareTo(product.price()) < 0) {
                xml.append("      <g:sale_price>").append(xml(moneyInr(product.salePrice()))).append("</g:sale_price>\n");
            }
            xml.append("      <g:link>").append(xml(productLink(product.product()))).append("</g:link>\n");
            xml.append("      <g:image_link>").append(xml(product.imageUrl())).append("</g:image_link>\n");
            for (String additionalImageUrl : product.additionalImageUrls()) {
                xml.append("      <g:additional_image_link>").append(xml(additionalImageUrl)).append("</g:additional_image_link>\n");
            }
            xml.append("      <g:brand>").append(xml("KRISHNAI Pearl Shopee")).append("</g:brand>\n");
            xml.append("      <g:google_product_category>").append(xml(facebookCategory(product.category()))).append("</g:google_product_category>\n");
            xml.append("      <g:product_type>").append(xml(displayCategory(product.category()))).append("</g:product_type>\n");
            xml.append("      <g:inventory>").append(product.product().getQuantity()).append("</g:inventory>\n");
            xml.append("      <g:custom_label_0>").append(xml(displayCategory(product.category()))).append("</g:custom_label_0>\n");
            xml.append("      <g:custom_label_1>").append(xml(product.offerLabel())).append("</g:custom_label_1>\n");
            xml.append("      <g:custom_label_2>website</g:custom_label_2>\n");
            xml.append("    </item>\n");
        }
        xml.append("  </channel>\n");
        xml.append("</rss>\n");
        return xml.toString();
    }

    private String buildCsvFeed() {
        List<FeedProduct> products = readyFeedProducts();
        StringBuilder csv = new StringBuilder("id,title,description,availability,condition,price,sale_price,link,image_link,additional_image_link,brand,google_product_category,product_type,inventory,custom_label_0,custom_label_1,custom_label_2\n");
        for (FeedProduct product : products) {
            List<String> cells = List.of(
                    feedId(product.product()),
                    product.product().getName(),
                    description(product.product()),
                    "in stock",
                    "new",
                    moneyInr(product.price()),
                    product.salePrice() != null && product.salePrice().compareTo(product.price()) < 0 ? moneyInr(product.salePrice()) : "",
                    productLink(product.product()),
                    product.imageUrl(),
                    String.join(",", product.additionalImageUrls()),
                    "KRISHNAI Pearl Shopee",
                    facebookCategory(product.category()),
                    displayCategory(product.category()),
                    String.valueOf(product.product().getQuantity()),
                    displayCategory(product.category()),
                    product.offerLabel(),
                    "website"
            );
            csv.append(cells.stream().map(this::csv).collect(Collectors.joining(","))).append("\n");
        }
        return csv.toString();
    }

    private List<FeedProduct> readyFeedProducts() {
        ReceiptSettings settings = settingsOrNull();
        Map<String, ProductCategoryOption> categories = syncedCategoriesByCode();
        return allFeedProducts(settings, categories).stream()
                .filter(product -> product.issue() == null)
                .toList();
    }

    private List<FeedProduct> allFeedProducts(ReceiptSettings settings, Map<String, ProductCategoryOption> categories) {
        LocalDate today = LocalDate.now();
        List<Offer> offers = offerRepository.findActiveOffers(today);
        return productRepository.findAll().stream()
                .sorted(Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(product -> buildFeedProduct(settings, categories, offers, product))
                .toList();
    }

    private FeedProduct buildFeedProduct(ReceiptSettings settings,
                                         Map<String, ProductCategoryOption> categories,
                                         List<Offer> offers,
                                         Product product) {
        ProductCategoryOption category = categories.get(normalizeCode(product.getCategory()));
        String imageUrl = fullImageUrl(primaryImage(product));
        BigDecimal price = money(product.getResolvedWebsitePrice());
        BigDecimal discount = bestDiscount(product, price, offers);
        BigDecimal salePrice = discount.compareTo(BigDecimal.ZERO) > 0
                ? price.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                : null;
        String issue = feedIssue(settings, category, product, imageUrl, price);
        List<String> additionalImageUrls = additionalImages(product).stream()
                .map(this::fullImageUrl)
                .filter(url -> !isBlank(url))
                .filter(url -> !url.equals(imageUrl))
                .distinct()
                .limit(10)
                .toList();
        String offerLabel = discount.compareTo(BigDecimal.ZERO) > 0 ? "Active offer" : "";
        return new FeedProduct(product, category, price, salePrice, imageUrl, additionalImageUrls, offerLabel, issue);
    }

    private String feedIssue(ReceiptSettings settings,
                             ProductCategoryOption category,
                             Product product,
                             String imageUrl,
                             BigDecimal price) {
        if (settings == null || !Boolean.TRUE.equals(settings.getFacebookCatalogEnabled())) {
            return "Feed disabled";
        }
        if (!Boolean.TRUE.equals(product.getActive())) {
            return "Hidden product";
        }
        if (!Boolean.TRUE.equals(product.getShowOnWebsite())) {
            return "Website visibility off";
        }
        if (Boolean.FALSE.equals(product.getFacebookSyncEnabled())) {
            return "Product Sync Off";
        }
        if (category == null) {
            return "Category Sync Off";
        }
        if (product.getQuantity() == null || product.getQuantity() <= 0) {
            return "Out of stock";
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            return "Missing Price";
        }
        if (isBlank(imageUrl)) {
            return "Missing Image";
        }
        return null;
    }

    private Map<String, ProductCategoryOption> syncedCategoriesByCode() {
        return productCategoryOptionRepository.findAll().stream()
                .filter(category -> Boolean.TRUE.equals(category.getActive()))
                .filter(category -> category.getFacebookSyncEnabled() == null || Boolean.TRUE.equals(category.getFacebookSyncEnabled()))
                .collect(Collectors.toMap(
                        category -> normalizeCode(category.getCode()),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }

    private void validatePublicAccess(String token) {
        ReceiptSettings settings = settingsOrNull();
        if (settings == null || !Boolean.TRUE.equals(settings.getFacebookCatalogEnabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Facebook catalog feed is disabled");
        }
        if (!isBlank(settings.getFacebookFeedToken())
                && (isBlank(token) || !settings.getFacebookFeedToken().equals(token))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid Facebook catalog feed token");
        }
    }

    private BigDecimal bestDiscount(Product product, BigDecimal price, List<Offer> offers) {
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return offers.stream()
                .filter(offer -> appliesTo(offer, product))
                .filter(offer -> offer.getMinOrderValue() == null || price.compareTo(offer.getMinOrderValue()) >= 0)
                .map(offer -> discountFor(offer, price))
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private boolean appliesTo(Offer offer, Product product) {
        if (offer == null || product == null) {
            return false;
        }
        if (offer.getProduct() != null) {
            return offer.getProduct().getId().equals(product.getId());
        }
        if (!isBlank(offer.getCategory())) {
            return offer.getCategory().equalsIgnoreCase(product.getCategory());
        }
        return true;
    }

    private BigDecimal discountFor(Offer offer, BigDecimal price) {
        DiscountType discountType = offer.getDiscountType();
        BigDecimal value = offer.getDiscountValue();
        if (discountType == null || value == null) {
            discountType = switch (offer.getType()) {
                case FLAT -> DiscountType.FLAT;
                case PERCENT, CATEGORY -> DiscountType.PERCENT;
                case BUY_ONE_GET_ONE, BUY_X_GET_Y, COMBO -> null;
            };
            value = offer.getValue();
        }
        if (discountType == null || value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal discount = discountType == DiscountType.PERCENT
                ? price.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : value;
        if (discountType == DiscountType.PERCENT && offer.getMaxDiscountAmount() != null) {
            discount = discount.min(offer.getMaxDiscountAmount());
        }
        return discount.min(price).setScale(2, RoundingMode.HALF_UP);
    }

    private String primaryImage(Product product) {
        List<String> images = parseProductImages(product.getProductImagesJson());
        if (!images.isEmpty()) {
            return images.get(0);
        }
        return product.getImageDataUrl();
    }

    private List<String> additionalImages(Product product) {
        List<String> images = new ArrayList<>(parseProductImages(product.getProductImagesJson()));
        if (images.isEmpty() || !isBlank(product.getImageDataUrl()) && images.size() == 1 && images.get(0).equals(product.getImageDataUrl())) {
            return List.of();
        }
        images.remove(0);
        return images;
    }

    private List<String> parseProductImages(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String fullImageUrl(String value) {
        if (isBlank(value) || value.startsWith("data:")) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("http://")) {
            return trimmed.replaceFirst("^http://", "https://");
        }
        if (trimmed.startsWith("/")) {
            return baseUrl() + trimmed;
        }
        return baseUrl() + "/" + trimmed;
    }

    private String productLink(Product product) {
        return baseUrl() + "/products/" + product.getId();
    }

    private String baseUrl() {
        String normalized = isBlank(publicBaseUrl) ? "https://kpskrishnai.com" : publicBaseUrl.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String feedId(Product product) {
        return !isBlank(product.getSku()) ? product.getSku() : "KPS-" + product.getId();
    }

    private String description(Product product) {
        if (!isBlank(product.getAiDescription())) {
            return product.getAiDescription().replaceAll("\\s+", " ").trim();
        }
        if (!isBlank(product.getDescription())) {
            return product.getDescription().replaceAll("\\s+", " ").trim();
        }
        return product.getName() + " from KRISHNAI Pearl Shopee.";
    }

    private String displayCategory(ProductCategoryOption category) {
        if (category == null) {
            return "";
        }
        return !isBlank(category.getFacebookCollectionName()) ? category.getFacebookCollectionName() : category.getDisplayName();
    }

    private String facebookCategory(ProductCategoryOption category) {
        if (category == null) {
            return "Apparel & Accessories > Jewelry";
        }
        if (!isBlank(category.getFacebookCategory())) {
            return category.getFacebookCategory();
        }
        String value = String.valueOf(category.getDisplayName()).toLowerCase(Locale.ROOT);
        if (value.contains("neck") || value.contains("mala") || value.contains("mangalsutra") || value.contains("pearl")) {
            return "Apparel & Accessories > Jewelry > Necklaces";
        }
        if (value.contains("ear") || value.contains("jhum")) {
            return "Apparel & Accessories > Jewelry > Earrings";
        }
        if (value.contains("bangle") || value.contains("bracelet") || value.contains("bali")) {
            return "Apparel & Accessories > Jewelry > Bracelets";
        }
        if (value.contains("ring")) {
            return "Apparel & Accessories > Jewelry > Rings";
        }
        if (value.contains("cosmetic") || value.contains("lip") || value.contains("kajal") || value.contains("makeup")
                || value.contains("beauty") || value.contains("skin")) {
            return "Health & Beauty > Personal Care > Cosmetics";
        }
        return "Apparel & Accessories > Jewelry";
    }

    private void touchLastGeneratedAt() {
        ReceiptSettings settings = settingsOrNull();
        if (settings != null) {
            settings.setFacebookFeedLastGeneratedAt(LocalDateTime.now());
            receiptSettingsRepository.save(settings);
        }
    }

    private ReceiptSettings settingsOrCreate() {
        return receiptSettingsRepository.findAll().stream().findFirst().orElseGet(() -> {
            ReceiptSettings settings = new ReceiptSettings();
            settings.setShopName("KRISHNAI Pearl Shopee");
            settings.setAddress("Pune");
            settings.setShowAddress(Boolean.TRUE);
            settings.setShowPhoneNumber(Boolean.TRUE);
            settings.setShowGstNumber(Boolean.FALSE);
            settings.setTaxEnabled(Boolean.FALSE);
            settings.setDeliveryFeeEnabled(Boolean.FALSE);
            settings.setFacebookCatalogEnabled(Boolean.FALSE);
            return settings;
        });
    }

    private ReceiptSettings settingsOrNull() {
        return receiptSettingsRepository.findAll().stream().findFirst().orElse(null);
    }

    private void clearCache() {
        cachedXml = null;
        cachedCsv = null;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String moneyInr(BigDecimal value) {
        return money(value).toPlainString() + " INR";
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private String xml(String value) {
        return String.valueOf(value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private record FeedProduct(Product product,
                               ProductCategoryOption category,
                               BigDecimal price,
                               BigDecimal salePrice,
                               String imageUrl,
                               List<String> additionalImageUrls,
                               String offerLabel,
                               String issue) {
    }

    private record CachedFeed(String content, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
