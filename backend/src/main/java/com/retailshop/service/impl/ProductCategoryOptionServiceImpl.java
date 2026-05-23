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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
@Slf4j
public class ProductCategoryOptionServiceImpl implements ProductCategoryOptionService {

    private static final Duration CATEGORY_ICON_OPENAI_TIMEOUT = Duration.ofSeconds(60);

    private final ProductCategoryOptionRepository productCategoryOptionRepository;
    private final MarketingProperties marketingProperties;
    private final ObjectMapper objectMapper;
    private final ImageUploadService imageUploadService;
    private final HttpClient httpClient;

    @Autowired
    public ProductCategoryOptionServiceImpl(ProductCategoryOptionRepository productCategoryOptionRepository,
                                            MarketingProperties marketingProperties,
                                            ObjectMapper objectMapper,
                                            ImageUploadService imageUploadService) {
        this(productCategoryOptionRepository, marketingProperties, objectMapper, imageUploadService, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build());
    }

    ProductCategoryOptionServiceImpl(ProductCategoryOptionRepository productCategoryOptionRepository,
                                     MarketingProperties marketingProperties,
                                     ObjectMapper objectMapper,
                                     ImageUploadService imageUploadService,
                                     HttpClient httpClient) {
        this.productCategoryOptionRepository = productCategoryOptionRepository;
        this.marketingProperties = marketingProperties;
        this.objectMapper = objectMapper;
        this.imageUploadService = imageUploadService;
        this.httpClient = httpClient;
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
        applyFacebookCatalogDefaults(category, request);
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
        applyFacebookCatalogDefaults(category, request);
        category.setActive(request.getActive() == null ? category.getActive() : request.getActive());
        return mapToResponse(productCategoryOptionRepository.save(category));
    }

    @Override
    @Transactional
    public java.util.List<CategoryIconOptionResponse> generateIconOptions(String categoryName, String primaryColor, String accentColor, String detailColor) {
        String displayName = normalizeDisplayName(categoryName);
        if (displayName.isBlank()) {
            throw new BusinessException("Category name is required");
        }
        try {
            return List.of(generateCategoryIcon(
                    displayName,
                    "preview",
                    normalizeHexColor(primaryColor, "#C97D3A"),
                    normalizeHexColor(accentColor, "#E8A44A"),
                    normalizeHexColor(detailColor, "#4A2C1A")
            ));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Category icon generation was interrupted");
        } catch (Exception exception) {
            log.warn("Category icon generation failed for {}", displayName, exception);
            throw new BusinessException("Category icon generation failed: " + exception.getMessage());
        }
    }

    @Override
    @Transactional
    public ProductCategoryOptionResponse generateIcon(java.util.UUID id, boolean replaceExisting) {
        ProductCategoryOption category = productCategoryOptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product category not found"));
        if (!replaceExisting && !isBlank(category.getIconImageUrl())) {
            throw new BusinessException("Category already has an icon. Use regenerate to replace it.");
        }
        String displayName = normalizeDisplayName(category.getDisplayName());
        CategoryIconOptionResponse generated;
        try {
            generated = generateCategoryIcon(displayName, "category-" + category.getId(), "#C97D3A", "#E8A44A", "#4A2C1A");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Category icon generation was interrupted");
        } catch (Exception exception) {
            log.warn("Category icon generation failed for {}", displayName, exception);
            throw new BusinessException("Category icon generation failed: " + exception.getMessage());
        }
        category.setIconImageUrl(generated.getImageUrl());
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
                .iconImageUrl(category.getIconImageUrl())
                .facebookSyncEnabled(Boolean.TRUE.equals(category.getFacebookSyncEnabled()))
                .facebookCategory(category.getFacebookCategory())
                .facebookCollectionName(category.getFacebookCollectionName())
                .active(category.getActive())
                .createdAt(category.getCreatedAt())
                .build();
    }

    private void applyFacebookCatalogDefaults(ProductCategoryOption category, ProductCategoryOptionRequest request) {
        boolean syncEnabled = request.getFacebookSyncEnabled() == null || Boolean.TRUE.equals(request.getFacebookSyncEnabled());
        category.setFacebookSyncEnabled(syncEnabled);
        if (!syncEnabled) {
            category.setFacebookCategory(normalizeOptionalText(request.getFacebookCategory()));
            category.setFacebookCollectionName(normalizeOptionalText(request.getFacebookCollectionName()));
            return;
        }
        String displayName = normalizeDisplayName(category.getDisplayName());
        String facebookCategory = normalizeOptionalText(request.getFacebookCategory());
        String collectionName = normalizeOptionalText(request.getFacebookCollectionName());
        category.setFacebookCategory(facebookCategory == null ? defaultFacebookCategory(displayName) : facebookCategory);
        category.setFacebookCollectionName(collectionName == null ? displayName + " Collection" : collectionName);
    }

    private String defaultFacebookCategory(String displayName) {
        String normalized = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        if (normalized.contains("neck") || normalized.contains("mala") || normalized.contains("mangalsutra") || normalized.contains("pearl")) {
            return "Apparel & Accessories > Jewelry > Necklaces";
        }
        if (normalized.contains("ear") || normalized.contains("jhum")) {
            return "Apparel & Accessories > Jewelry > Earrings";
        }
        if (normalized.contains("bangle") || normalized.contains("bracelet") || normalized.contains("bali")) {
            return "Apparel & Accessories > Jewelry > Bracelets";
        }
        if (normalized.contains("ring")) {
            return "Apparel & Accessories > Jewelry > Rings";
        }
        if (normalized.contains("cosmetic") || normalized.contains("lip") || normalized.contains("kajal") || normalized.contains("makeup")
                || normalized.contains("beauty") || normalized.contains("skin")) {
            return "Health & Beauty > Personal Care > Cosmetics";
        }
        return "Apparel & Accessories > Jewelry";
    }

    private String normalizeDisplayName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeOptionalText(String value) {
        String normalized = value == null ? null : value.trim();
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String normalizeHexColor(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.matches("^#[0-9A-Fa-f]{6}$") ? normalized.toUpperCase(Locale.ROOT) : fallback;
    }

    private CategoryIconOptionResponse generateCategoryIcon(String categoryName,
                                                            String seed,
                                                            String primaryColor,
                                                            String accentColor,
                                                            String detailColor) throws IOException, InterruptedException {
        if (!isOpenAiImageConfigured()) {
            throw new IOException("OpenAI category icon generation is not configured. Configure marketing AI image settings before generating category icons.");
        }
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return generateOpenAiCategoryIcon(categoryName, seed, primaryColor, accentColor, detailColor);
            } catch (IOException exception) {
                lastFailure = exception;
                log.warn("OpenAI category icon generation attempt {} failed for {}: {}", attempt, categoryName, exception.getMessage());
            }
        }
        log.warn("OpenAI category icon generation failed after retries for {}. Using local fallback icon.", categoryName, lastFailure);
        return generateFallbackCategoryIcon(categoryName, seed, primaryColor, accentColor, detailColor);
    }

    private CategoryIconOptionResponse generateOpenAiCategoryIcon(String categoryName,
                                                                  String seed,
                                                                  String primaryColor,
                                                                  String accentColor,
                                                                  String detailColor) throws IOException, InterruptedException {
        String prompt = buildCategoryIconPrompt(categoryName, seed, primaryColor, accentColor, detailColor);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", defaultString(marketingProperties.getAi().getImageModel(), "gpt-image-1"));
        payload.put("prompt", prompt);
        payload.put("size", defaultString(marketingProperties.getAi().getImageSize(), "1024x1024"));
        payload.put("quality", defaultString(marketingProperties.getAi().getImageQuality(), "medium"));
        payload.put("output_format", "png");
        payload.put("background", "transparent");
        payload.put("n", 1);

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/images/generations"))
                .header("authorization", "Bearer " + marketingProperties.getAi().getApiKey().trim())
                .header("content-type", "application/json")
                .timeout(CATEGORY_ICON_OPENAI_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException(extractApiErrorMessage(response.body()));
        }
        byte[] imageBytes = makeCircularTransparentPng(extractOpenAiImageBytes(response.body()));
        ImageUploadResponse uploadResponse = imageUploadService.uploadImageBytes(imageBytes, "image/png", "category-icons");
        if (uploadResponse == null || isBlank(uploadResponse.getCloudfrontUrl())) {
            throw new IOException("generated icon could not be uploaded");
        }
        return CategoryIconOptionResponse.builder()
                .label("OpenAI Icon")
                .imageUrl(uploadResponse.getCloudfrontUrl().trim())
                .build();
    }

    private CategoryIconOptionResponse generateFallbackCategoryIcon(String categoryName, String seed, String primaryColor, String accentColor, String detailColor) throws IOException {
        byte[] imageBytes = createLocalCategoryIconPng(categoryName, seed, primaryColor, accentColor, detailColor);
        ImageUploadResponse uploadResponse = imageUploadService.uploadImageBytes(imageBytes, "image/png", "category-icons");
        if (uploadResponse == null || isBlank(uploadResponse.getCloudfrontUrl())) {
            throw new IOException("fallback category icon could not be uploaded");
        }
        return CategoryIconOptionResponse.builder()
                .label("Generated Icon")
                .imageUrl(uploadResponse.getCloudfrontUrl().trim())
                .build();
    }

    private byte[] createLocalCategoryIconPng(String categoryName, String seed, String primaryColor, String accentColor, String detailColor) throws IOException {
        int size = 1024;
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Color primary = Color.decode(primaryColor);
            Color accent = Color.decode(accentColor);
            Color detail = Color.decode(detailColor);
            drawFallbackSymbol(graphics, categoryName, size, primary, accent, detail);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (!ImageIO.write(output, "png", outputStream)) {
            throw new IOException("fallback category icon could not be encoded");
        }
        return outputStream.toByteArray();
    }

    private void drawFallbackSymbol(Graphics2D graphics, String categoryName, int size, Color primary, Color accent, Color detail) {
        String normalized = categoryName == null ? "" : categoryName.toLowerCase(Locale.ROOT);
        Color line = new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 238);
        Color fine = new Color(detail.getRed(), detail.getGreen(), detail.getBlue(), 210);
        Color gem = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 230);
        graphics.setColor(line);
        graphics.setStroke(new BasicStroke(40, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (normalized.contains("ear") || normalized.contains("jhum") || normalized.contains("bali")) {
            graphics.drawLine(330, 230, 330, 315);
            graphics.drawLine(690, 230, 690, 315);
            graphics.draw(new Ellipse2D.Double(260, 330, 140, 210));
            graphics.draw(new Ellipse2D.Double(620, 330, 140, 210));
            graphics.setStroke(new BasicStroke(24, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(gem);
            graphics.draw(new Ellipse2D.Double(290, 560, 80, 80));
            graphics.draw(new Ellipse2D.Double(650, 560, 80, 80));
            graphics.fillOval(315, 680, 34, 34);
            graphics.fillOval(675, 680, 34, 34);
            return;
        }
        if (normalized.contains("bangle") || normalized.contains("bracelet")) {
            graphics.draw(new Ellipse2D.Double(255, 300, 500, 360));
            graphics.setColor(fine);
            graphics.setStroke(new BasicStroke(24, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(new Ellipse2D.Double(310, 350, 390, 250));
            graphics.drawArc(245, 310, 520, 340, 205, 130);
            graphics.drawArc(245, 310, 520, 340, 25, 130);
            return;
        }
        if (normalized.contains("ring")) {
            graphics.draw(new Ellipse2D.Double(330, 365, 360, 360));
            graphics.setColor(gem);
            graphics.setStroke(new BasicStroke(30, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int[] xs = {510, 590, 640, 580, 500, 430, 470};
            int[] ys = {205, 205, 285, 355, 355, 285, 205};
            graphics.drawPolygon(xs, ys, xs.length);
            graphics.setColor(fine);
            graphics.drawLine(430, 285, 640, 285);
            return;
        }
        if (normalized.contains("lip") || normalized.contains("cosmetic") || normalized.contains("makeup")
                || normalized.contains("beauty") || normalized.contains("skin") || normalized.contains("kajal")) {
            graphics.drawRoundRect(390, 330, 240, 410, 76, 76);
            graphics.setColor(fine);
            graphics.setStroke(new BasicStroke(24, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawLine(420, 495, 600, 495);
            graphics.setColor(gem);
            graphics.drawRoundRect(455, 210, 110, 150, 52, 52);
            return;
        }
        graphics.drawArc(240, 190, 540, 600, 210, 120);
        graphics.setColor(gem);
        graphics.setStroke(new BasicStroke(30, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Ellipse2D.Double(445, 585, 130, 165));
        graphics.setColor(fine);
        graphics.setStroke(new BasicStroke(22, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(510, 585, 510, 505);
        graphics.draw(new Ellipse2D.Double(285, 470, 55, 55));
        graphics.draw(new Ellipse2D.Double(680, 470, 55, 55));
    }

    private String buildCategoryIconPrompt(String categoryName, String seed, String primaryColor, String accentColor, String detailColor) {
        String subject = productSymbolFor(categoryName);
        return """
                Create one premium ecommerce category icon for KRISHNAI Pearl Shopee.
                Category: %s.
                Depict: %s.
                Style: simple jewellery line-art icon like a premium ecommerce category glyph, thin outline drawing, no shading, no photo realism, no 3D, mobile readability at 48px.
                Palette: primary stroke %s, gem/accent %s, dark detail %s.
                Composition: centered single object or simple paired objects, no hands, no people, no model, no background scene.
                Technical: square PNG, fully transparent background, icon artwork only, no card, no badge, no outer border, no enclosing circular border, no curved decorative border lines, no filled square or filled circle background, no text, no letters, no logo, no watermark, no price tags, no extra decorative clutter.
                Seed/context: %s.
                """.formatted(categoryName, subject, primaryColor, accentColor, detailColor, seed);
    }

    private String productSymbolFor(String categoryName) {
        String normalized = categoryName == null ? "" : categoryName.toLowerCase(Locale.ROOT);
        if (normalized.contains("neck") || normalized.contains("mala") || normalized.contains("mangalsutra") || normalized.contains("pearl")) {
            return "a pearl necklace or elegant necklace pendant";
        }
        if (normalized.contains("ear") || normalized.contains("jhum") || normalized.contains("bali")) {
            return "a pair of earrings with a delicate pearl or jhumka-inspired silhouette";
        }
        if (normalized.contains("bangle") || normalized.contains("bracelet")) {
            return "stacked bangles or a refined bracelet";
        }
        if (normalized.contains("ring")) {
            return "a graceful ring with a small pearl setting";
        }
        if (normalized.contains("lip")) {
            return "a premium lipstick";
        }
        if (normalized.contains("kajal") || normalized.contains("eye")) {
            return "a cosmetic eye pencil with a clean beauty symbol";
        }
        if (normalized.contains("cosmetic") || normalized.contains("makeup") || normalized.contains("beauty") || normalized.contains("skin")) {
            return "a compact cosmetic palette and brush";
        }
        return "a tasteful symbol combining pearl jewelry and boutique beauty retail";
    }

    private byte[] makeCircularTransparentPng(byte[] sourceBytes) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(sourceBytes));
        if (source == null) {
            throw new IOException("generated icon image could not be decoded");
        }
        int size = Math.min(source.getWidth(), source.getHeight());
        int sourceX = Math.max((source.getWidth() - size) / 2, 0);
        int sourceY = Math.max((source.getHeight() - size) / 2, 0);
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, size, size, sourceX, sourceY, sourceX + size, sourceY + size, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (!ImageIO.write(output, "png", outputStream)) {
            throw new IOException("generated circular icon could not be encoded");
        }
        return outputStream.toByteArray();
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

    private boolean isOpenAiImageConfigured() {
        return marketingProperties != null
                && marketingProperties.getAi() != null
                && marketingProperties.getAi().isEnabled()
                && !isBlank(marketingProperties.getAi().getApiKey())
                && !isBlank(marketingProperties.getAi().getImageModel());
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
