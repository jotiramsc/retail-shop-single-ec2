package com.retailshop.controller;

import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.ProductCategoryOptionRequest;
import com.retailshop.dto.ProductCategoryOptionResponse;
import com.retailshop.service.ProductCategoryOptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/product-categories")
@RequiredArgsConstructor
public class ProductCategoryOptionController {

    private final ProductCategoryOptionService productCategoryOptionService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_PRODUCTS')")
    public PaginatedResponse<ProductCategoryOptionResponse> getCategories(@RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return productCategoryOptionService.getCategories(pageable);
    }

    @GetMapping("/options")
    public List<ProductCategoryOptionResponse> getActiveCategories() {
        return productCategoryOptionService.getActiveCategories();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_PRODUCTS')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductCategoryOptionResponse createCategory(@Valid @RequestBody ProductCategoryOptionRequest request) {
        return productCategoryOptionService.createCategory(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PRODUCTS')")
    public ProductCategoryOptionResponse updateCategory(@PathVariable UUID id,
                                                        @Valid @RequestBody ProductCategoryOptionRequest request) {
        return productCategoryOptionService.updateCategory(id, request);
    }
}
