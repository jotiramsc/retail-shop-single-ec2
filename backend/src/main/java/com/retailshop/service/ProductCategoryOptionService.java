package com.retailshop.service;

import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.CategoryIconOptionResponse;
import com.retailshop.dto.ProductCategoryOptionRequest;
import com.retailshop.dto.ProductCategoryOptionResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProductCategoryOptionService {
    PaginatedResponse<ProductCategoryOptionResponse> getCategories(Pageable pageable);
    List<ProductCategoryOptionResponse> getActiveCategories();
    ProductCategoryOptionResponse createCategory(ProductCategoryOptionRequest request);
    ProductCategoryOptionResponse updateCategory(UUID id, ProductCategoryOptionRequest request);
    default List<CategoryIconOptionResponse> generateIconOptions(String categoryName) {
        return generateIconOptions(categoryName, null, null, null);
    }
    List<CategoryIconOptionResponse> generateIconOptions(String categoryName, String primaryColor, String accentColor, String detailColor);
    ProductCategoryOptionResponse generateIcon(UUID id, boolean replaceExisting);
    void validateCategoryCode(String code);
}
