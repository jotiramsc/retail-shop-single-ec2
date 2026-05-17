package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.MarketingProperties;
import com.retailshop.dto.CategoryIconOptionResponse;
import com.retailshop.dto.ImageUploadResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.ProductCategoryOptionRequest;
import com.retailshop.dto.ProductCategoryOptionResponse;
import com.retailshop.entity.ProductCategoryOption;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.ProductCategoryOptionRepository;
import com.retailshop.service.ImageUploadService;
import com.retailshop.service.ProductCategoryOptionService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductCategoryOptionServiceImpl implements ProductCategoryOptionService {

    private final ProductCategoryOptionRepository productCategoryOptionRepository;
    private final MarketingProperties marketingProperties;
    private final ObjectMapper objectMapper;
    private final ImageUploadService imageUploadService;
    private final HttpClient httpClient;

    public ProductCategoryOptionServiceImpl(ProductCategoryOptionRepository productCategoryOptionRepository,
                                            MarketingProperties marketingProperties,
                                            ObjectMapper objectMapper,
                                            ImageUploadService imageUploadService) {
        this.productCategoryOptionRepository = productCategoryOptionRepository;
        this.marketingProperties = marketingProperties;
        this.objectMapper = objectMapper;
        this.imageUploadService = imageUploadService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

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
        if (marketingProperties == null
                || marketingProperties.getAi() == null
                || isBlank(marketingProperties.getAi().getApiKey())
                || isBlank(marketingProperties.getAi().getImageModel())) {
            throw new BusinessException("OpenAI image generation is not configured for category icons");
        }
        try {
            return List.of(
                    generateOpenAiCategoryIcon(displayName, 1, "minimal luxury line-art icon on a soft ivory background"),
                    generateOpenAiCategoryIcon(displayName, 2, "premium festive retail icon with warm gold accents"),
                    generateOpenAiCategoryIcon(displayName, 3, "modern boutique icon for mobile category cards"),
                    generateOpenAiCategoryIcon(displayName, 4, "elegant jewellery and cosmetics app icon style")
            );
        } catch (IOException exception) {
            throw new BusinessException("OpenAI category icon generation failed: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("OpenAI category icon generation was interrupted");
        }
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

    private CategoryIconOptionResponse generateOpenAiCategoryIcon(String categoryName,
                                                                  int optionNumber,
                                                                  String visualDirection) throws IOException, InterruptedException {
        String prompt = "Create one square category icon image for Krishnai Pearl Shopee, a luxury jewellery and cosmetics shop. "
                + "Category: " + categoryName + ". "
                + visualDirection + ". "
                + "Use a premium Indian boutique aesthetic, clear product-symbol silhouette, warm gold accents, clean mobile-app readability, no text, no logo, no watermark.";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", defaultString(marketingProperties.getAi().getImageModel(), "gpt-image-1.5"));
        payload.put("prompt", prompt);
        payload.put("size", defaultString(marketingProperties.getAi().getImageSize(), "1024x1024"));
        payload.put("quality", defaultString(marketingProperties.getAi().getImageQuality(), "medium"));
        payload.put("output_format", "png");

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/images/generations"))
                .header("authorization", "Bearer " + marketingProperties.getAi().getApiKey().trim())
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException(extractApiErrorMessage(response.body()));
        }
        byte[] imageBytes = extractOpenAiImageBytes(response.body());
        ImageUploadResponse uploadResponse = imageUploadService.uploadImageBytes(imageBytes, "image/png", "category-icons");
        if (uploadResponse == null || isBlank(uploadResponse.getCloudfrontUrl())) {
            throw new IOException("generated icon could not be uploaded");
        }
        return CategoryIconOptionResponse.builder()
                .label("AI Option " + optionNumber)
                .imageUrl(uploadResponse.getCloudfrontUrl().trim())
                .build();
    }

    private byte[] extractOpenAiImageBytes(String responseBody) throws IOException, InterruptedException {
        JsonNode imageNode = objectMapper.readTree(responseBody).path("data").path(0);
        String base64Image = trimToNull(imageNode.path("b64_json").asText(""));
        if (base64Image != null) {
            return Base64.getDecoder().decode(base64Image);
        }
        String remoteUrl = trimToNull(imageNode.path("url").asText(""));
        if (remoteUrl == null) {
            throw new IOException("OpenAI returned no image data");
        }
        HttpResponse<byte[]> imageResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(remoteUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        if (imageResponse.statusCode() >= 400 || imageResponse.body() == null || imageResponse.body().length == 0) {
            throw new IOException("OpenAI image download failed");
        }
        return imageResponse.body();
    }

    private String extractApiErrorMessage(String responseBody) {
        try {
            String message = trimToNull(objectMapper.readTree(responseBody).path("error").path("message").asText(""));
            return defaultString(message, "OpenAI image generation failed");
        } catch (IOException ignored) {
            return "OpenAI image generation failed";
        }
    }

    private String trimToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
