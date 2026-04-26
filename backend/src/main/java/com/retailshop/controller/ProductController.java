package com.retailshop.controller;

import com.retailshop.dto.ProductRequest;
import com.retailshop.dto.ProductResponse;
import com.retailshop.dto.PublicProductResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_PRODUCTS')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        return productService.createProduct(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_PRODUCTS', 'PERM_BILLING')")
    public PaginatedResponse<ProductResponse> getAllProducts(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 250));
        return productService.getAllProducts(pageable);
    }

    @GetMapping("/catalog")
    public List<PublicProductResponse> getPublicCatalog() {
        return productService.getPublicCatalog();
    }

    @GetMapping("/catalog/home")
    public List<PublicProductResponse> getPublicHomepageCatalog() {
        return productService.getPublicHomepageCatalog();
    }

    @GetMapping("/catalog/trending")
    public List<PublicProductResponse> getPublicTrendingCatalog() {
        return productService.getPublicTrendingProducts(3);
    }

    @GetMapping("/trending")
    @PreAuthorize("hasAnyAuthority('PERM_PRODUCTS', 'PERM_BILLING')")
    public List<ProductResponse> getTrendingProducts() {
        return productService.getTrendingProducts(10);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PRODUCTS')")
    public ProductResponse updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PRODUCTS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
    }
}
