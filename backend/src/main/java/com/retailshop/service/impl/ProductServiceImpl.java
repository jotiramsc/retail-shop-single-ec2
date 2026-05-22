package com.retailshop.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.dto.LowStockProductResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.ProductRequest;
import com.retailshop.dto.ProductResponse;
import com.retailshop.dto.PublicProductResponse;
import com.retailshop.entity.Offer;
import com.retailshop.entity.Product;
import com.retailshop.entity.ReceiptSettings;
import com.retailshop.enums.DiscountType;
import com.retailshop.enums.OrderStatus;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.OfferRepository;
import com.retailshop.repository.OrderItemRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.repository.ReceiptSettingsRepository;
import com.retailshop.service.ProductAiDescriptionService;
import com.retailshop.service.ProductCategoryOptionService;
import com.retailshop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final InvoiceItemRepository invoiceItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final OfferRepository offerRepository;
    private final ReceiptSettingsRepository receiptSettingsRepository;
    private final ProductCategoryOptionService productCategoryOptionService;
    private final ProductAiDescriptionService productAiDescriptionService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        validateRequest(request);
        productRepository.findBySku(request.getSku()).ifPresent(product -> {
            throw new BusinessException("Product with this SKU already exists");
        });
        Product product = new Product();
        mapRequest(product, request);
        Product savedProduct = productRepository.save(product);
        syncCustomerAccessProduct(savedProduct);
        queueAiDescriptionIfRequested(savedProduct, request);
        return mapToResponse(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ProductResponse> getAllProducts(Pageable pageable) {
        return PaginatedResponse.from(productRepository.findAllByActiveTrueOrderByCreatedAtDesc(pageable)
                .map(this::mapToResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getTrendingProducts(int limit) {
        Map<UUID, Product> products = activeProducts()
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<ProductResponse> trending = orderItemRepository.findTrendingProductSales(OrderStatus.CANCELLED)
                .stream()
                .map(row -> products.get((UUID) row[0]))
                .filter(product -> product != null)
                .limit(limit)
                .map(this::mapToResponse)
                .toList();

        if (trending.size() >= limit) {
            return trending;
        }

        List<UUID> selectedIds = trending.stream().map(ProductResponse::getId).toList();
        List<ProductResponse> fallback = activeProducts()
                .stream()
                .filter(product -> !selectedIds.contains(product.getId()))
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .limit(limit - trending.size())
                .map(this::mapToResponse)
                .toList();

        return java.util.stream.Stream.concat(trending.stream(), fallback.stream()).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicProductResponse> getPublicTrendingProducts(int limit) {
        Map<UUID, Product> products = activeProducts()
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<PublicProductResponse> trending = orderItemRepository.findTrendingProductSales(OrderStatus.CANCELLED)
                .stream()
                .map(row -> products.get((UUID) row[0]))
                .filter(product -> product != null)
                .filter(this::isVisibleOnWebsite)
                .filter(product -> isInStock(product.getQuantity()))
                .limit(limit)
                .map(this::mapToPublicResponse)
                .toList();

        if (trending.size() >= limit) {
            return trending;
        }

        List<UUID> selectedIds = trending.stream().map(PublicProductResponse::getId).toList();
        List<PublicProductResponse> fallback = products.values()
                .stream()
                .filter(this::isVisibleOnWebsite)
                .filter(product -> isInStock(product.getQuantity()))
                .filter(product -> !selectedIds.contains(product.getId()))
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .limit(limit - trending.size())
                .map(this::mapToPublicResponse)
                .toList();

        return Stream.concat(trending.stream(), fallback.stream()).toList();
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        validateRequest(request);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (!product.getSku().equals(request.getSku())) {
            productRepository.findBySku(request.getSku()).ifPresent(existing -> {
                throw new BusinessException("Product with this SKU already exists");
            });
        }
        mapRequest(product, request);
        Product savedProduct = productRepository.save(product);
        syncCustomerAccessProduct(savedProduct);
        queueAiDescriptionIfRequested(savedProduct, request);
        return mapToResponse(savedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        product.setActive(Boolean.FALSE);
        product.setShowOnWebsite(Boolean.FALSE);
        product.setUseForBilling(Boolean.FALSE);
        product.setShowInEditorsPicks(Boolean.FALSE);
        product.setShowInNewRelease(Boolean.FALSE);
        product.setShowInCustomerAccess(Boolean.FALSE);
        product.setShowInShopCollection(Boolean.FALSE);
        product.setShowInFeaturedPieces(Boolean.FALSE);
        product.setShowInStory(Boolean.FALSE);
        product.setShowInCuratedSelections(Boolean.FALSE);
        productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<LowStockProductResponse> getLowStockProducts(Pageable pageable) {
        Page<LowStockProductResponse> page = productRepository.findLowStockProducts(pageable)
                .map(product -> LowStockProductResponse.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .category(product.getCategory())
                        .sku(product.getSku())
                        .quantity(product.getQuantity())
                        .threshold(product.getLowStockThreshold())
                        .build());
        return PaginatedResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicProductResponse> getPublicCatalog() {
        return activeProducts()
                .stream()
                .filter(this::isVisibleOnWebsite)
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .map(this::mapToPublicResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicProductResponse> getPublicHomepageCatalog() {
        List<Product> products = activeProducts()
                .stream()
                .filter(this::isVisibleOnWebsite)
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .toList();

        List<Product> selectedProducts = Stream.of(
                        products.stream().filter(product -> Boolean.TRUE.equals(product.getShowInCustomerAccess())).limit(1),
                        products.stream().filter(product -> Boolean.TRUE.equals(product.getShowInShopCollection())).limit(3),
                        products.stream().filter(product -> Boolean.TRUE.equals(product.getShowInFeaturedPieces())).limit(8),
                        products.stream().filter(product -> Boolean.TRUE.equals(product.getShowInStory())).limit(1),
                        products.stream().filter(product -> Boolean.TRUE.equals(product.getShowInCuratedSelections())).limit(4),
                        products.stream().filter(product -> Boolean.TRUE.equals(product.getShowInNewRelease())).limit(8),
                        products.stream().limit(16)
                )
                .flatMap(Function.identity())
                .collect(Collectors.toMap(Product::getId, Function.identity(), (first, ignored) -> first))
                .values()
                .stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .limit(24)
                .toList();

        return selectedProducts.stream().map(this::mapToPublicResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProductResponse getPublicProduct(UUID id) {
        return productRepository.findById(id)
                .filter(this::isActive)
                .filter(this::isVisibleOnWebsite)
                .map(this::mapToPublicResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private void mapRequest(Product product, ProductRequest request) {
        productCategoryOptionService.validateCategoryCode(request.getCategory());
        product.setName(request.getName());
        product.setCategory(request.getCategory().trim().toUpperCase(java.util.Locale.ROOT));
        product.setSku(request.getSku());
        product.setCostPrice(request.getCostPrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setWebsitePricePercentage(normalizeWebsitePricePercentage(request.getWebsitePricePercentage()));
        product.setQuantity(request.getQuantity());
        product.setLowStockThreshold(request.getLowStockThreshold());
        List<String> productImages = normalizedProductImages(request.getProductImages(), request.getImageDataUrl());
        product.setImageDataUrl(productImages.isEmpty() ? null : productImages.get(0));
        product.setProductImagesJson(serializeProductImages(productImages));
        product.setDescription(normalizeOptionalText(request.getDescription()));
        product.setShowOnWebsite(request.getShowOnWebsite() == null || Boolean.TRUE.equals(request.getShowOnWebsite()));
        product.setUseForBilling(request.getUseForBilling() == null || Boolean.TRUE.equals(request.getUseForBilling()));
        product.setShowInEditorsPicks(Boolean.TRUE.equals(request.getShowInEditorsPicks()));
        product.setShowInNewRelease(Boolean.TRUE.equals(request.getShowInNewRelease()));
        product.setShowInCustomerAccess(Boolean.TRUE.equals(request.getShowInCustomerAccess()));
        product.setShowInShopCollection(Boolean.TRUE.equals(request.getShowInShopCollection()));
        product.setShowInFeaturedPieces(Boolean.TRUE.equals(request.getShowInFeaturedPieces()));
        product.setShowInStory(Boolean.TRUE.equals(request.getShowInStory()));
        product.setShowInCuratedSelections(Boolean.TRUE.equals(request.getShowInCuratedSelections()));
        product.setFacebookSyncEnabled(request.getFacebookSyncEnabled() == null || Boolean.TRUE.equals(request.getFacebookSyncEnabled()));
        product.setExpiryDate(request.getExpiryDate());
    }

    private ProductResponse mapToResponse(Product product) {
        DealDisplay deal = dealDisplay(product);
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .category(product.getCategory())
                .sku(product.getSku())
                .costPrice(product.getCostPrice())
                .sellingPrice(product.getSellingPrice())
                .websitePricePercentage(product.getWebsitePricePercentage())
                .websitePrice(product.getResolvedWebsitePrice())
                .quantity(product.getQuantity())
                .lowStockThreshold(product.getLowStockThreshold())
                .imageDataUrl(product.getImageDataUrl())
                .productImages(productImages(product))
                .description(product.getDescription())
                .aiDescriptionStatus(product.getAiDescriptionStatus())
                .aiDescription(product.getAiDescription())
                .aiDescriptionGeneratedAt(product.getAiDescriptionGeneratedAt())
                .aiDescriptionError(product.getAiDescriptionError())
                .originalPrice(deal.originalPrice())
                .offerPrice(deal.offerPrice())
                .discountPercent(deal.discountPercent())
                .youSave(deal.youSave())
                .offerName(deal.offerName())
                .couponCode(deal.couponCode())
                .freeDeliveryEligible(deal.freeDeliveryEligible())
                .showOnWebsite(product.getShowOnWebsite())
                .useForBilling(product.getUseForBilling())
                .showInEditorsPicks(product.getShowInEditorsPicks())
                .showInNewRelease(product.getShowInNewRelease())
                .showInCustomerAccess(product.getShowInCustomerAccess())
                .showInShopCollection(product.getShowInShopCollection())
                .showInFeaturedPieces(product.getShowInFeaturedPieces())
                .showInStory(product.getShowInStory())
                .showInCuratedSelections(product.getShowInCuratedSelections())
                .facebookSyncEnabled(Boolean.TRUE.equals(product.getFacebookSyncEnabled()))
                .active(product.getActive())
                .expiryDate(product.getExpiryDate())
                .createdAt(product.getCreatedAt())
                .build();
    }

    private PublicProductResponse mapToPublicResponse(Product product) {
        DealDisplay deal = dealDisplay(product);
        return PublicProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .category(product.getCategory())
                .sku(product.getSku())
                .sellingPrice(deal.offerPrice())
                .originalPrice(deal.originalPrice())
                .offerPrice(deal.offerPrice())
                .discountPercent(deal.discountPercent())
                .youSave(deal.youSave())
                .offerName(deal.offerName())
                .couponCode(deal.couponCode())
                .freeDeliveryEligible(deal.freeDeliveryEligible())
                .quantity(product.getQuantity())
                .inStock(isInStock(product.getQuantity()))
                .stockLabel(customerStockLabel(product.getQuantity(), product.getLowStockThreshold()))
                .imageDataUrl(publicImageUrl(product.getImageDataUrl()))
                .productImages(publicProductImages(product))
                .description(product.getDescription())
                .aiDescriptionStatus(product.getAiDescriptionStatus())
                .aiDescription(product.getAiDescription())
                .aiDescriptionGeneratedAt(product.getAiDescriptionGeneratedAt())
                .showInEditorsPicks(product.getShowInEditorsPicks())
                .showInNewRelease(product.getShowInNewRelease())
                .showInCustomerAccess(product.getShowInCustomerAccess())
                .showInShopCollection(product.getShowInShopCollection())
                .showInFeaturedPieces(product.getShowInFeaturedPieces())
                .showInStory(product.getShowInStory())
                .showInCuratedSelections(product.getShowInCuratedSelections())
                .createdAt(product.getCreatedAt())
                .build();
    }

    private DealDisplay dealDisplay(Product product) {
        BigDecimal originalPrice = money(product.getResolvedWebsitePrice());
        Offer bestOffer = bestDisplayOffer(product, originalPrice);
        BigDecimal discount = bestOffer == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : discountFor(bestOffer, originalPrice);
        BigDecimal offerPrice = originalPrice.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountPercent = originalPrice.compareTo(BigDecimal.ZERO) > 0 && discount.compareTo(BigDecimal.ZERO) > 0
                ? discount.multiply(BigDecimal.valueOf(100)).divide(originalPrice, 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
        BigDecimal freeDeliveryThreshold = receiptSettingsRepository.findAll().stream()
                .findFirst()
                .filter(settings -> Boolean.TRUE.equals(settings.getDeliveryFeeEnabled()))
                .map(ReceiptSettings::getFreeDeliveryThreshold)
                .orElse(BigDecimal.ZERO);
        return new DealDisplay(
                originalPrice,
                offerPrice,
                discountPercent,
                discount,
                bestOffer != null ? bestOffer.getName() : null,
                bestOffer != null ? bestOffer.getCouponCode() : null,
                freeDeliveryThreshold != null
                        && freeDeliveryThreshold.compareTo(BigDecimal.ZERO) > 0
                        && offerPrice.compareTo(freeDeliveryThreshold) >= 0
        );
    }

    private Offer bestDisplayOffer(Product product, BigDecimal basePrice) {
        if (basePrice.compareTo(BigDecimal.ZERO) <= 0) {
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
        if (offer.getCategory() != null && !offer.getCategory().isBlank()) {
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

    private String publicImageUrl(String imageDataUrl) {
        if (imageDataUrl == null || imageDataUrl.isBlank()) {
            return imageDataUrl;
        }
        return imageDataUrl.startsWith("data:image/") ? null : imageDataUrl;
    }

    private List<String> normalizedProductImages(List<String> productImages, String primaryImage) {
        java.util.LinkedHashSet<String> images = new java.util.LinkedHashSet<>();
        if (primaryImage != null && !primaryImage.isBlank()) {
            images.add(primaryImage.trim());
        }
        if (productImages != null) {
            productImages.stream()
                    .filter(image -> image != null && !image.isBlank())
                    .map(String::trim)
                    .forEach(images::add);
        }
        return images.stream().limit(12).toList();
    }

    private String serializeProductImages(List<String> productImages) {
        if (productImages == null || productImages.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(productImages);
        } catch (Exception exception) {
            throw new BusinessException("Unable to save product images");
        }
    }

    private List<String> productImages(Product product) {
        List<String> parsed = parseProductImages(product.getProductImagesJson());
        if (parsed.isEmpty() && product.getImageDataUrl() != null && !product.getImageDataUrl().isBlank()) {
            return List.of(product.getImageDataUrl());
        }
        return parsed;
    }

    private List<String> publicProductImages(Product product) {
        return productImages(product).stream()
                .map(this::publicImageUrl)
                .filter(image -> image != null && !image.isBlank())
                .toList();
    }

    private List<String> parseProductImages(String productImagesJson) {
        if (productImagesJson == null || productImagesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(productImagesJson, new TypeReference<List<String>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private void queueAiDescriptionIfRequested(Product product, ProductRequest request) {
        if (Boolean.TRUE.equals(request.getGenerateAiDescription())) {
            productAiDescriptionService.queueDescriptionGeneration(product.getId());
        }
    }

    private String normalizeOptionalText(String value) {
        String normalized = value == null ? null : value.trim();
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private boolean isVisibleOnWebsite(Product product) {
        return isActive(product) && Boolean.TRUE.equals(product.getShowOnWebsite());
    }

    private boolean isActive(Product product) {
        return product != null && !Boolean.FALSE.equals(product.getActive());
    }

    private List<Product> activeProducts() {
        return productRepository.findAll()
                .stream()
                .filter(this::isActive)
                .toList();
    }

    private void syncCustomerAccessProduct(Product activeProduct) {
        if (!Boolean.TRUE.equals(activeProduct.getShowInCustomerAccess())) {
            return;
        }

        List<Product> productsToClear = productRepository.findAll()
                .stream()
                .filter(this::isActive)
                .filter(product -> !product.getId().equals(activeProduct.getId()))
                .filter(product -> Boolean.TRUE.equals(product.getShowInCustomerAccess()))
                .toList();

        if (productsToClear.isEmpty()) {
            return;
        }

        productsToClear.forEach(product -> product.setShowInCustomerAccess(Boolean.FALSE));
        productRepository.saveAll(productsToClear);
    }

    private void validateRequest(ProductRequest request) {
        if (request.getCategory() == null || request.getCategory().isBlank()) {
            throw new BusinessException("Product category is required");
        }
        if (request.getSellingPrice().compareTo(request.getCostPrice()) < 0) {
            throw new BusinessException("Shop price cannot be lower than cost price");
        }
    }

    private BigDecimal normalizeWebsitePricePercentage(BigDecimal websitePricePercentage) {
        if (websitePricePercentage == null || websitePricePercentage.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return websitePricePercentage;
    }

    private boolean isInStock(Integer quantity) {
        return quantity != null && quantity > 0;
    }

    private String customerStockLabel(Integer quantity, Integer lowStockThreshold) {
        int availableQuantity = quantity == null ? 0 : quantity;
        if (availableQuantity <= 0) {
            return "Out of stock";
        }

        int threshold = lowStockThreshold == null ? 0 : lowStockThreshold;
        int lowStockCutoff = Math.max(1, Math.min(5, threshold > 0 ? threshold : 5));
        if (availableQuantity <= lowStockCutoff) {
            return "Last few remaining";
        }
        return "Available now";
    }

    private record DealDisplay(BigDecimal originalPrice,
                               BigDecimal offerPrice,
                               BigDecimal discountPercent,
                               BigDecimal youSave,
                               String offerName,
                               String couponCode,
                               Boolean freeDeliveryEligible) {
    }
}
