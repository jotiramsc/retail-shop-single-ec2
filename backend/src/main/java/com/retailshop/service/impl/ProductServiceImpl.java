package com.retailshop.service.impl;

import com.retailshop.dto.LowStockProductResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.ProductRequest;
import com.retailshop.dto.ProductResponse;
import com.retailshop.dto.PublicProductResponse;
import com.retailshop.entity.Product;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.ProductCategoryOptionService;
import com.retailshop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final ProductRepository productRepository;
    private final ProductCategoryOptionService productCategoryOptionService;

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
        return mapToResponse(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ProductResponse> getAllProducts(Pageable pageable) {
        return PaginatedResponse.from(productRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::mapToResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getTrendingProducts(int limit) {
        Map<UUID, Product> products = productRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<ProductResponse> trending = invoiceItemRepository.findTrendingProductSales()
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
        List<ProductResponse> fallback = productRepository.findAll()
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
        return getTrendingProducts(limit)
                .stream()
                .map(product -> PublicProductResponse.builder()
                        .id(product.getId())
                        .name(product.getName())
                        .category(product.getCategory())
                        .sku(product.getSku())
                        .sellingPrice(product.getWebsitePrice())
                        .quantity(product.getQuantity())
                        .inStock(isInStock(product.getQuantity()))
                        .stockLabel(customerStockLabel(product.getQuantity(), product.getLowStockThreshold()))
                        .imageDataUrl(publicImageUrl(product.getImageDataUrl()))
                        .showInEditorsPicks(product.getShowInEditorsPicks())
                        .showInNewRelease(product.getShowInNewRelease())
                        .showInCustomerAccess(product.getShowInCustomerAccess())
                        .showInShopCollection(product.getShowInShopCollection())
                        .showInFeaturedPieces(product.getShowInFeaturedPieces())
                        .showInStory(product.getShowInStory())
                        .showInCuratedSelections(product.getShowInCuratedSelections())
                        .createdAt(product.getCreatedAt())
                        .build())
                .toList();
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
        return mapToResponse(savedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (invoiceItemRepository.existsByProductId(id)) {
            throw new BusinessException("This product is already used in invoices and cannot be deleted");
        }

        productRepository.delete(product);
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
        return productRepository.findAll()
                .stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .map(this::mapToPublicResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicProductResponse> getPublicHomepageCatalog() {
        List<Product> products = productRepository.findAll()
                .stream()
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
        product.setImageDataUrl(request.getImageDataUrl());
        product.setShowInEditorsPicks(Boolean.TRUE.equals(request.getShowInEditorsPicks()));
        product.setShowInNewRelease(Boolean.TRUE.equals(request.getShowInNewRelease()));
        product.setShowInCustomerAccess(Boolean.TRUE.equals(request.getShowInCustomerAccess()));
        product.setShowInShopCollection(Boolean.TRUE.equals(request.getShowInShopCollection()));
        product.setShowInFeaturedPieces(Boolean.TRUE.equals(request.getShowInFeaturedPieces()));
        product.setShowInStory(Boolean.TRUE.equals(request.getShowInStory()));
        product.setShowInCuratedSelections(Boolean.TRUE.equals(request.getShowInCuratedSelections()));
        product.setExpiryDate(request.getExpiryDate());
    }

    private ProductResponse mapToResponse(Product product) {
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
                .showInEditorsPicks(product.getShowInEditorsPicks())
                .showInNewRelease(product.getShowInNewRelease())
                .showInCustomerAccess(product.getShowInCustomerAccess())
                .showInShopCollection(product.getShowInShopCollection())
                .showInFeaturedPieces(product.getShowInFeaturedPieces())
                .showInStory(product.getShowInStory())
                .showInCuratedSelections(product.getShowInCuratedSelections())
                .expiryDate(product.getExpiryDate())
                .createdAt(product.getCreatedAt())
                .build();
    }

    private PublicProductResponse mapToPublicResponse(Product product) {
        return PublicProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .category(product.getCategory())
                .sku(product.getSku())
                .sellingPrice(product.getResolvedWebsitePrice())
                .quantity(product.getQuantity())
                .inStock(isInStock(product.getQuantity()))
                .stockLabel(customerStockLabel(product.getQuantity(), product.getLowStockThreshold()))
                .imageDataUrl(publicImageUrl(product.getImageDataUrl()))
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

    private String publicImageUrl(String imageDataUrl) {
        if (imageDataUrl == null || imageDataUrl.isBlank()) {
            return imageDataUrl;
        }
        return imageDataUrl.startsWith("data:image/") ? null : imageDataUrl;
    }

    private void syncCustomerAccessProduct(Product activeProduct) {
        if (!Boolean.TRUE.equals(activeProduct.getShowInCustomerAccess())) {
            return;
        }

        List<Product> productsToClear = productRepository.findAll()
                .stream()
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
}
