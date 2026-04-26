package com.retailshop.service;

import com.retailshop.dto.LowStockProductResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.ProductRequest;
import com.retailshop.dto.ProductResponse;
import com.retailshop.dto.PublicProductResponse;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProductService {
    ProductResponse createProduct(ProductRequest request);
    PaginatedResponse<ProductResponse> getAllProducts(Pageable pageable);
    List<ProductResponse> getTrendingProducts(int limit);
    List<PublicProductResponse> getPublicTrendingProducts(int limit);
    ProductResponse updateProduct(UUID id, ProductRequest request);
    void deleteProduct(UUID id);
    PaginatedResponse<LowStockProductResponse> getLowStockProducts(Pageable pageable);
    List<PublicProductResponse> getPublicCatalog();
    List<PublicProductResponse> getPublicHomepageCatalog();
}
