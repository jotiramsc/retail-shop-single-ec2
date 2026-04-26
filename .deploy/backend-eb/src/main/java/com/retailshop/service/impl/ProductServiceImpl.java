package com.retailshop.service.impl;

import com.retailshop.dto.LowStockProductResponse;
import com.retailshop.dto.ProductRequest;
import com.retailshop.dto.ProductResponse;
import com.retailshop.dto.PublicProductResponse;
import com.retailshop.entity.Product;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final InvoiceItemRepository invoiceItemRepository;
    private final ProductRepository productRepository;

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
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
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
                .filter(product -> product.getQuantity() != null && product.getQuantity() > 0)
                .map(product -> PublicProductResponse.builder()
                        .id(product.getId())
                        .name(product.getName())
                        .category(product.getCategory())
                        .sku(product.getSku())
                        .sellingPrice(product.getSellingPrice())
                        .imageDataUrl(product.getImageDataUrl())
                        .showInEditorsPicks(product.getShowInEditorsPicks())
                        .showInNewRelease(product.getShowInNewRelease())
                        .showInCustomerAccess(product.getShowInCustomerAccess())
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
    public List<LowStockProductResponse> getLowStockProducts() {
        return productRepository.findLowStockProducts()
                .stream()
                .map(product -> LowStockProductResponse.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .category(product.getCategory())
                        .sku(product.getSku())
                        .quantity(product.getQuantity())
                        .threshold(product.getLowStockThreshold())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicProductResponse> getPublicCatalog() {
        return productRepository.findAll()
                .stream()
                .filter(product -> product.getQuantity() != null && product.getQuantity() > 0)
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .map(product -> PublicProductResponse.builder()
                        .id(product.getId())
                        .name(product.getName())
                        .category(product.getCategory())
                        .sku(product.getSku())
                        .sellingPrice(product.getSellingPrice())
                        .imageDataUrl(product.getImageDataUrl())
                        .showInEditorsPicks(product.getShowInEditorsPicks())
                        .showInNewRelease(product.getShowInNewRelease())
                        .showInCustomerAccess(product.getShowInCustomerAccess())
                        .createdAt(product.getCreatedAt())
                        .build())
                .toList();
    }

    private void mapRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setSku(request.getSku());
        product.setCostPrice(request.getCostPrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setQuantity(request.getQuantity());
        product.setLowStockThreshold(request.getLowStockThreshold());
        product.setImageDataUrl(request.getImageDataUrl());
        product.setShowInEditorsPicks(Boolean.TRUE.equals(request.getShowInEditorsPicks()));
        product.setShowInNewRelease(Boolean.TRUE.equals(request.getShowInNewRelease()));
        product.setShowInCustomerAccess(Boolean.TRUE.equals(request.getShowInCustomerAccess()));
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
                .quantity(product.getQuantity())
                .lowStockThreshold(product.getLowStockThreshold())
                .imageDataUrl(product.getImageDataUrl())
                .showInEditorsPicks(product.getShowInEditorsPicks())
                .showInNewRelease(product.getShowInNewRelease())
                .showInCustomerAccess(product.getShowInCustomerAccess())
                .expiryDate(product.getExpiryDate())
                .createdAt(product.getCreatedAt())
                .build();
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
        if (request.getSellingPrice().compareTo(request.getCostPrice()) < 0) {
            throw new BusinessException("Selling price cannot be lower than cost price");
        }
    }
}
