package com.retailshop.service.impl;

import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.CategoryIconOptionResponse;
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
        category.setIconImageUrl(normalizeOptionalText(request.getIconImageUrl()));
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
        category.setIconImageUrl(normalizeOptionalText(request.getIconImageUrl()));
        category.setActive(request.getActive() == null ? category.getActive() : request.getActive());
        return mapToResponse(productCategoryOptionRepository.save(category));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<CategoryIconOptionResponse> generateIconOptions(String categoryName) {
        String displayName = normalizeDisplayName(categoryName);
        if (displayName.isBlank()) {
            throw new BusinessException("Category name is required");
        }
        String[] palettes = {"8a2f4d:f6d77b", "126b49:e8c06a", "6d3c82:f4d0e5", "9b4d13:f8d083"};
        return java.util.stream.IntStream.range(0, palettes.length)
                .mapToObj(index -> CategoryIconOptionResponse.builder()
                        .label("Option " + (index + 1))
                        .imageUrl(buildIconDataUri(displayName, palettes[index], index))
                        .build())
                .toList();
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
                .iconImageUrl(category.getIconImageUrl())
                .active(category.getActive())
                .createdAt(category.getCreatedAt())
                .build();
    }

    private String normalizeDisplayName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeOptionalText(String value) {
        String normalized = value == null ? null : value.trim();
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String buildIconDataUri(String categoryName, String palette, int index) {
        String[] colors = palette.split(":");
        String initials = categoryName.replaceAll("[^A-Za-z0-9 ]", "").trim();
        initials = initials.isBlank() ? "K" : initials.substring(0, Math.min(2, initials.length())).toUpperCase(Locale.ROOT);
        String motif = switch (index) {
            case 1 -> "<path d='M32 18c14 0 22 10 22 22s-8 22-22 22-22-10-22-22 8-22 22-22z' fill='none' stroke='%23" + colors[1] + "' stroke-width='4'/>";
            case 2 -> "<path d='M14 44c8-18 28-18 36 0M22 48h20' fill='none' stroke='%23" + colors[1] + "' stroke-width='4' stroke-linecap='round'/>";
            case 3 -> "<path d='M18 23h28l-6 31H24z' fill='none' stroke='%23" + colors[1] + "' stroke-width='4'/>";
            default -> "<path d='M16 40c6-18 26-18 32 0M22 44h20' fill='none' stroke='%23" + colors[1] + "' stroke-width='4' stroke-linecap='round'/>";
        };
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96' viewBox='0 0 64 64'>"
                + "<rect width='64' height='64' rx='18' fill='%23" + colors[0] + "'/>"
                + motif
                + "<circle cx='32' cy='47' r='7' fill='%23" + colors[1] + "'/>"
                + "<text x='32' y='58' text-anchor='middle' font-family='serif' font-size='9' font-weight='700' fill='white'>" + initials + "</text>"
                + "</svg>";
        return "data:image/svg+xml;utf8," + svg.replace("#", "%23").replace(" ", "%20");
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
