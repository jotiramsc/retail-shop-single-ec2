package com.retailshop.service;

import com.retailshop.dto.LowStockProductResponse;
import com.retailshop.dto.ProductRequest;
import com.retailshop.dto.ProductResponse;
import com.retailshop.dto.PublicProductResponse;

import java.util.List;
import java.util.UUID;

public interface ProductService {
    ProductResponse createProduct(ProductRequest request);
    List<ProductResponse> getAllProducts();
    List<ProductResponse> getTrendingProducts(int limit);
    List<PublicProductResponse> getPublicTrendingProducts(int limit);
    ProductResponse updateProduct(UUID id, ProductRequest request);
    void deleteProduct(UUID id);
    List<LowStockProductResponse> getLowStockProducts();
    List<PublicProductResponse> getPublicCatalog();
}
