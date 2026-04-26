package com.retailshop.service.impl;

import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.ProductCategoryOptionRequest;
import com.retailshop.dto.ProductCategoryOptionResponse;
import com.retailshop.entity.ProductCategoryOption;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.ProductCategoryOptionRepository;
import com.retailshop.service.ProductCategoryOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductCategoryOptionServiceImpl implements ProductCategoryOptionService {

    private final ProductCategoryOptionRepository productCategoryOptionRepository;

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ProductCategoryOptionResponse> getCategories(Pageable pageable) {
        return PaginatedResponse.from(productCategoryOptionRepository.findAllByOrderByDisplayNameAsc(pageable)
                .map(this::mapToResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<ProductCategoryOptionResponse> getActiveCategories() {
        return productCategoryOptionRepository.findByActiveTrueOrderByDisplayNameAsc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductCategoryOptionResponse createCategory(ProductCategoryOptionRequest request) {
        String displayName = normalizeDisplayName(request.getDisplayName());
        if (productCategoryOptionRepository.existsByDisplayNameIgnoreCase(displayName)) {
            throw new BusinessException("A category with this name already exists");
        }

        ProductCategoryOption category = new ProductCategoryOption();
        category.setCode(toCode(displayName));
        if (productCategoryOptionRepository.existsByCode(category.getCode())) {
            throw new BusinessException("A category with this code already exists");
        }
        category.setDisplayName(displayName);
        category.setActive(request.getActive() == null ? Boolean.TRUE : request.getActive());
        return mapToResponse(productCategoryOptionRepository.save(category));
    }

    @Override
    @Transactional
    public ProductCategoryOptionResponse updateCategory(UUID id, ProductCategoryOptionRequest request) {
        ProductCategoryOption category = productCategoryOptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        String displayName = normalizeDisplayName(request.getDisplayName());
        if (!category.getDisplayName().equalsIgnoreCase(displayName)
                && productCategoryOptionRepository.existsByDisplayNameIgnoreCase(displayName)) {
            throw new BusinessException("A category with this name already exists");
        }
        category.setDisplayName(displayName);
        category.setActive(request.getActive() == null ? category.getActive() : request.getActive());
        return mapToResponse(productCategoryOptionRepository.save(category));
    }

    @Override
    @Transactional(readOnly = true)
    public void validateCategoryCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("Product category is required");
        }
        ProductCategoryOption category = productCategoryOptionRepository.findByCode(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException("Choose a valid product category"));
        if (!Boolean.TRUE.equals(category.getActive())) {
            throw new BusinessException("Selected product category is inactive");
        }
    }

    private ProductCategoryOptionResponse mapToResponse(ProductCategoryOption category) {
        return ProductCategoryOptionResponse.builder()
                .id(category.getId())
                .code(category.getCode())
                .displayName(category.getDisplayName())
                .active(category.getActive())
                .createdAt(category.getCreatedAt())
                .build();
    }

    private String normalizeDisplayName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String toCode(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String code = normalized.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (code.isBlank()) {
            throw new BusinessException("Category name must contain letters or numbers");
        }
        return code;
    }
}
