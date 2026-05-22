package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.MarketingProperties;
import com.retailshop.dto.ImageUploadResponse;
import com.retailshop.entity.Campaign;
import com.retailshop.entity.ReceiptSettings;
import com.retailshop.enums.MarketingPlatform;
import com.retailshop.repository.ReceiptSettingsRepository;
import com.retailshop.service.AIContentGenerationService;
import com.retailshop.service.ImageUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.AttributedString;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class AIContentGenerationServiceImpl implements AIContentGenerationService {

    private static final Duration CAMPAIGN_IMAGE_OPENAI_TIMEOUT = Duration.ofSeconds(12);

    private final MarketingProperties marketingProperties;
    private final ObjectMapper objectMapper;
    private final ImageUploadService imageUploadService;
    private final ReceiptSettingsRepository receiptSettingsRepository;
    private final HttpClient httpClient;

    @Autowired
    public AIContentGenerationServiceImpl(MarketingProperties marketingProperties,
                                          ObjectMapper objectMapper,
                                          ImageUploadService imageUploadService,
                                          ReceiptSettingsRepository receiptSettingsRepository) {
        this(marketingProperties, objectMapper, imageUploadService, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build(), receiptSettingsRepository);
    }

    AIContentGenerationServiceImpl(MarketingProperties marketingProperties,
                                   ObjectMapper objectMapper,
                                   ImageUploadService imageUploadService) {
        this(marketingProperties, objectMapper, imageUploadService, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build(), null);
    }

    AIContentGenerationServiceImpl(MarketingProperties marketingProperties,
                                   ObjectMapper objectMapper,
                                   ImageUploadService imageUploadService,
                                   HttpClient httpClient) {
        this(marketingProperties, objectMapper, imageUploadService, httpClient, null);
    }

    AIContentGenerationServiceImpl(MarketingProperties marketingProperties,
                                   ObjectMapper objectMapper,
                                   ImageUploadService imageUploadService,
                                   HttpClient httpClient,
                                   ReceiptSettingsRepository receiptSettingsRepository) {
        this.marketingProperties = marketingProperties;
        this.objectMapper = objectMapper;
        this.imageUploadService = imageUploadService;
        this.receiptSettingsRepository = receiptSettingsRepository;
        this.httpClient = httpClient;
    }

    @Override
    public GeneratedMarketingDraft generateDraft(Campaign campaign,
                                                 String shopName,
                                                 String categoryName,
                                                 String productName,
                                                 MarketingPlatform platform) {
        if (marketingProperties.getAi().isEnabled() && !isBlank(marketingProperties.getAi().getApiKey())) {
            try {
                return generateWithOpenAi(campaign, shopName, categoryName, productName, platform);
            } catch (Exception exception) {
                log.warn("Falling back to mock AI generation for campaign {}", campaign.getId(), exception);
            }
        }
        return generateMock(campaign, shopName, categoryName, productName, platform);
    }

    @Override
    public GeneratedCreativeImage generateSharedCreativeImage(Campaign campaign,
                                                              String shopName,
                                                              String categoryName,
                                                              String productName,
                                                              String visualSeed) {
        MarketingOccasionLibrary.Occasion festivalContext = detectFestivalContext(campaign);
        String resolvedVisualSeed = defaultString(visualSeed, UUID.randomUUID().toString());
        String imagePrompt = buildSharedImagePrompt(campaign, shopName, categoryName, productName, festivalContext, resolvedVisualSeed);
        String imageUrl = generateCreativeImage(campaign, shopName, productName, MarketingPlatform.INSTAGRAM, imagePrompt);
        return new GeneratedCreativeImage(imagePrompt, imageUrl);
    }

    private GeneratedMarketingDraft generateWithOpenAi(Campaign campaign,
                                                       String shopName,
                                                       String categoryName,
                                                       String productName,
                                                       MarketingPlatform platform) throws IOException, InterruptedException {
        MarketingOccasionLibrary.Occasion festivalContext = detectFestivalContext(campaign);
        String systemPrompt = """
                You generate premium social marketing drafts for an Indian ladies jewelry and cosmetics retail business.
                Return valid JSON only with keys:
                captionText, hashtags, callToAction, imagePrompt.
                Keep content brand-safe and never invent fake discounts.
                If the selected language is MARATHI, write natural Marathi in Devanagari script, not translated corporate English.
                Use correct Marathi grammar and spelling. For Mother's Day, use "मातृ दिन" or "मातृदिन" and never "मातु दिन".
                For Mother's Day Marathi copy, avoid repeated phrases like "मातृ दिनासाठी आईसाठी"; prefer "आईसाठी खास भेट" or "आईसाठी खास भेटवस्तूंवर".
                Never use awkward phrases like "जपरी", "तिने नेहमी जपरी", or "तिची देखभाल करा"; use "तिने नेहमी आपली काळजी घेतली" and "आता तिच्यासाठी सुंदर भेट निवडा".
                Understand Indian festival and occasion names mentioned in the campaign name or offer title and reflect that context correctly in the wording, emotion, gifting angle, and visual mood.
                Never repeat the festival name back-to-back or create awkward patterns like "{festival} निमित्त {festival}".
                If the offer title already contains the festival name, avoid echoing the same name again in the first line.
                Write thumb-stopping captions with a strong first-line hook, a quote-like emotional line when useful, and a clear shopping pull.
                Keep Instagram captions short, elegant, and scroll-stopping, with a memorable first sentence.
                Keep Facebook captions slightly longer, warm, persuasive, and ready for a real campaign.
                Avoid dull phrasing like "खास ऑफर उपलब्ध", "आजच खरेदी करा" as the whole caption, or plain title repetition.
                Do not use static-sounding templates; make every caption feel like polished campaign copy.
                Do not write or request the shop name as image text. The website may overlay the real logo separately.
                Avoid CTA wording like "Share this message" or "हा संदेश शेअर करा"; use a visit-now shopping CTA instead.
                This is a jewellery and cosmetics shopee. Use words like jewellery, necklace, bangles, earrings, cosmetics, gifting, styling, and collection where relevant.
                Avoid wording that implies a precious-metal shop.
                Use at most one tasteful emoji in Marathi captions, only where it adds charm.
                """;
        String userPrompt = buildUserPrompt(campaign, shopName, categoryName, productName, platform, festivalContext);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", defaultString(marketingProperties.getAi().getModel(), "gpt-4.1-mini"));
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("temperature", 0.8);
        payload.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        });

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("authorization", "Bearer " + marketingProperties.getAi().getApiKey().trim())
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("OpenAI generation failed with status " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IOException("OpenAI returned empty content");
        }
        JsonNode result = objectMapper.readTree(content);
        String caption = trimToNull(result.path("captionText").asText(""));
        String hashtags = trimToNull(result.path("hashtags").asText(""));
        String cta = trimToNull(result.path("callToAction").asText(""));
        String imagePrompt = trimToNull(result.path("imagePrompt").asText(""));
        String resolvedCaption = polishGeneratedCaption(defaultString(caption, buildMockCaption(campaign, shopName, productName, platform, festivalContext)), campaign, productName, platform, festivalContext);
        resolvedCaption = ensureMarathiCaptionQuality(resolvedCaption, campaign, shopName, productName, platform, festivalContext);
        String resolvedHashtags = defaultString(hashtags, buildMockHashtags(campaign, categoryName, platform, festivalContext));
        String resolvedCta = polishGeneratedCta(defaultString(cta, buildMockCta(campaign, platform, festivalContext)), campaign, platform, festivalContext);
        String resolvedImagePrompt = defaultString(imagePrompt, buildSharedImagePrompt(campaign, shopName, categoryName, productName, festivalContext, "draft-" + platform.name()));
        String resolvedImageUrl = buildCreativePreview(campaign, shopName, productName, platform);

        return new GeneratedMarketingDraft(
                resolvedCaption,
                resolvedHashtags,
                resolvedCta,
                resolvedImagePrompt,
                resolvedImageUrl
        );
    }

    private GeneratedMarketingDraft generateMock(Campaign campaign,
                                                 String shopName,
                                                 String categoryName,
                                                 String productName,
                                                 MarketingPlatform platform) {
        MarketingOccasionLibrary.Occasion festivalContext = detectFestivalContext(campaign);
        String imagePrompt = buildSharedImagePrompt(campaign, shopName, categoryName, productName, festivalContext, "draft-" + platform.name());
        String caption = ensureMarathiCaptionQuality(
                buildMockCaption(campaign, shopName, productName, platform, festivalContext),
                campaign,
                shopName,
                productName,
                platform,
                festivalContext
        );
        String cta = polishGeneratedCta(buildMockCta(campaign, platform, festivalContext), campaign, platform, festivalContext);
        return new GeneratedMarketingDraft(
                caption,
                buildMockHashtags(campaign, categoryName, platform, festivalContext),
                cta,
                imagePrompt,
                buildCreativePreview(campaign, shopName, productName, platform)
        );
    }

    private String generateCreativeImage(Campaign campaign,
                                         String shopName,
                                         String productName,
                                         MarketingPlatform platform,
                                         String imagePrompt) {
        String fallbackPreview = buildCreativePreview(campaign, shopName, productName, platform);
        if (isBlank(imagePrompt)) {
            return fallbackPreview;
        }

        try {
            GeneratedImagePayload imagePayload = generateOpenAiImagePayload(imagePrompt);
            if (imagePayload == null) {
                return fallbackPreview;
            }
            if (imagePayload.imageBytes() != null && imagePayload.imageBytes().length > 0) {
                ImageUploadResponse uploadResponse = imageUploadService.uploadImageBytes(
                        imagePayload.imageBytes(),
                        defaultString(imagePayload.contentType(), "image/png"),
                        "marketing-campaigns"
                );
                if (uploadResponse != null && !isBlank(uploadResponse.getCloudfrontUrl())) {
                    return uploadResponse.getCloudfrontUrl().trim();
                }
            }
            if (!isBlank(imagePayload.sourceUrl())) {
                return imagePayload.sourceUrl().trim();
            }
            return fallbackPreview;
        } catch (Exception exception) {
            log.warn("Falling back to inline preview for generated marketing image on campaign {}", campaign.getId(), exception);
            return fallbackPreview;
        }
    }

    private byte[] composeCampaignCreativeImage(Campaign campaign,
                                                String shopName,
                                                String productName,
                                                MarketingPlatform platform,
                                                byte[] backgroundBytes) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(backgroundBytes));
        if (source == null) {
            throw new IOException("Generated image could not be decoded");
        }

        int size = Math.min(source.getWidth(), source.getHeight());
        int cropX = Math.max(0, (source.getWidth() - size) / 2);
        int cropY = Math.max(0, (source.getHeight() - size) / 2);
        BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            applyImageRenderingHints(graphics);
            graphics.drawImage(source, 0, 0, size, size, cropX, cropY, cropX + size, cropY + size, null);
            drawImageTextOverlays(graphics, campaign, shopName, productName, platform, size);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", output);
        return output.toByteArray();
    }

    private void drawImageTextOverlays(Graphics2D graphics,
                                       Campaign campaign,
                                       String shopName,
                                       String productName,
                                       MarketingPlatform platform,
                                       int size) {
        int margin = Math.max(48, size / 18);
        Color deepShadow = new Color(23, 14, 10, 188);
        Color warmGold = new Color(255, 230, 166);
        Color ivory = new Color(255, 248, 226);
        MarketingOccasionLibrary.Occasion festivalContext = detectFestivalContext(campaign);

        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 172), 0, size / 3f, new Color(0, 0, 0, 0)));
        graphics.fillRect(0, 0, size, size / 3);
        graphics.setPaint(new GradientPaint(0, size * 0.62f, new Color(0, 0, 0, 0), 0, size, new Color(0, 0, 0, 192)));
        graphics.fillRect(0, (int) (size * 0.62), size, (int) (size * 0.38));

        drawShopLogo(graphics, margin, margin, Math.max(88, size / 10));

        boolean allowMarathiOverlay = !"MARATHI".equals(resolveLanguage(campaign)) || supportsDevanagariOverlay();
        Font headlineFont = allowMarathiOverlay
                ? preferredFont(Font.BOLD, Math.max(34, size / 24), "Noto Sans Devanagari", "Nirmala UI", "SansSerif")
                : preferredFont(Font.BOLD, Math.max(34, size / 24), "DejaVu Sans", "Liberation Sans", "SansSerif");
        Font ctaFont = allowMarathiOverlay
                ? preferredFont(Font.BOLD, Math.max(22, size / 40), "Noto Sans Devanagari", "Nirmala UI", "SansSerif")
                : preferredFont(Font.BOLD, Math.max(22, size / 40), "DejaVu Sans", "Liberation Sans", "SansSerif");

        drawOfferBadge(graphics, campaign, size, margin, warmGold, allowMarathiOverlay);

        String imageQuote = buildCreativeImageQuote(campaign, festivalContext);
        if (!allowMarathiOverlay && containsDevanagari(imageQuote)) {
            imageQuote = buildEnglishImageQuote(campaign, festivalContext);
        }
        if (!isBlank(imageQuote)) {
            Font quoteFont = containsDevanagari(imageQuote)
                    ? preferredFont(Font.BOLD, Math.max(46, size / 18), "Noto Serif Devanagari", "Noto Sans Devanagari", "SansSerif")
                    : preferredFont(Font.BOLD, Math.max(52, size / 17), "DejaVu Serif", "Liberation Serif", Font.SERIF);
            int quoteY = Math.max(margin + Math.max(152, size / 7), (int) (size * 0.28));
            drawWrappedText(graphics, imageQuote, quoteFont, new Color(0, 0, 0, 178), margin + 3, quoteY + 3, size - (margin * 2), 2, TextAlign.CENTER);
            drawWrappedText(graphics, imageQuote, quoteFont, ivory, margin, quoteY, size - (margin * 2), 2, TextAlign.CENTER);
        }

        String headline = buildCreativeImageHeadline(campaign, productName, festivalContext);
        if (!isBlank(imageQuote) && comparableText(headline).equals(comparableText(imageQuote))) {
            headline = buildCreativeImageSecondaryLine(campaign, festivalContext);
        }
        if (!allowMarathiOverlay && containsDevanagari(headline)) {
            headline = buildEnglishImageHeadline(campaign, productName, festivalContext);
        }
        int bottomY = size - margin - Math.max(116, size / 8);
        drawWrappedText(graphics, headline, headlineFont, new Color(0, 0, 0, 170), margin + 3, bottomY + 3, size - (margin * 2), 2, TextAlign.CENTER);
        drawWrappedText(graphics, headline, headlineFont, warmGold, margin, bottomY, size - (margin * 2), 2, TextAlign.CENTER);

        String cta = defaultString(trimToNull(buildMockCta(campaign, platform, festivalContext)),
                resolveLanguage(campaign).equals("MARATHI") ? "आजच भेट द्या" : "Visit today");
        if (!allowMarathiOverlay && containsDevanagari(cta)) {
            cta = buildEnglishImageCta(campaign, platform);
        }
        int ctaY = size - margin - Math.max(34, size / 32);
        drawRoundedTextBar(graphics, cta, ctaFont, deepShadow, ivory, margin, ctaY, size - (margin * 2));
    }

    private String buildCreativeImageQuote(Campaign campaign,
                                           MarketingOccasionLibrary.Occasion festivalContext) {
        if (!isNoOfferCampaign(campaign)) {
            return null;
        }
        return switch (campaignGoal(campaign)) {
            case "GREETING" -> "MARATHI".equals(resolveLanguage(campaign))
                    ? buildMarathiGreetingHeadline(festivalContext)
                    : buildEnglishGreetingHeadline(campaign, festivalContext);
            case "QUOTE" -> "MARATHI".equals(resolveLanguage(campaign))
                    ? "सौंदर्य छोट्या तपशीलांतून खुलतं"
                    : "Beauty lives in the little details";
            default -> null;
        };
    }

    private String buildEnglishImageQuote(Campaign campaign,
                                          MarketingOccasionLibrary.Occasion festivalContext) {
        if (!isNoOfferCampaign(campaign)) {
            return null;
        }
        return switch (campaignGoal(campaign)) {
            case "GREETING" -> buildEnglishGreetingHeadline(campaign, festivalContext);
            case "QUOTE" -> "Beauty lives in the little details";
            default -> null;
        };
    }

    private String buildCreativeImageSecondaryLine(Campaign campaign,
                                                   MarketingOccasionLibrary.Occasion festivalContext) {
        if ("MARATHI".equals(resolveLanguage(campaign))) {
            return switch (campaignGoal(campaign)) {
                case "GREETING" -> festivalContext == null ? "आनंदाचा हा क्षण साजरा करा" : "आनंद आणि शुभेच्छा साजऱ्या करा";
                case "QUOTE" -> "तुमच्या सुंदर क्षणांसाठी";
                default -> "कलेक्शन पाहा";
            };
        }
        return switch (campaignGoal(campaign)) {
            case "GREETING" -> "Celebrate the moment";
            case "QUOTE" -> "For your beautiful everyday moments";
            default -> "Explore the collection";
        };
    }

    private void drawShopLogo(Graphics2D graphics, int x, int y, int size) {
        BufferedImage logo = loadLogoImage();
        if (logo == null) {
            return;
        }
        int padding = Math.max(10, size / 12);
        graphics.setColor(new Color(255, 250, 239, 232));
        graphics.fillRoundRect(x, y, size, size, Math.max(18, size / 5), Math.max(18, size / 5));
        graphics.setColor(new Color(246, 213, 154, 150));
        graphics.setStroke(new java.awt.BasicStroke(Math.max(2f, size / 52f)));
        graphics.drawRoundRect(x, y, size, size, Math.max(18, size / 5), Math.max(18, size / 5));
        graphics.drawImage(logo, x + padding, y + padding, size - (padding * 2), size - (padding * 2), null);
    }

    private BufferedImage loadLogoImage() {
        String logoUrl = resolveLogoUrl();
        if (isBlank(logoUrl)) {
            return null;
        }
        try {
            if (logoUrl.startsWith("data:image/")) {
                int commaIndex = logoUrl.indexOf(',');
                if (commaIndex < 0) {
                    return null;
                }
                String metadata = logoUrl.substring(0, commaIndex);
                String data = logoUrl.substring(commaIndex + 1);
                byte[] bytes = metadata.contains(";base64")
                        ? Base64.getDecoder().decode(data)
                        : java.net.URLDecoder.decode(data, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
                return ImageIO.read(new ByteArrayInputStream(bytes));
            }
            if (logoUrl.startsWith("http://") || logoUrl.startsWith("https://")) {
                HttpResponse<byte[]> response = httpClient.send(
                        HttpRequest.newBuilder(URI.create(logoUrl)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                );
                if (response.statusCode() < 400 && response.body() != null && response.body().length > 0) {
                    return ImageIO.read(new ByteArrayInputStream(response.body()));
                }
            }
        } catch (Exception exception) {
            log.debug("Unable to load logo for campaign creative overlay", exception);
        }
        return null;
    }

    private String resolveLogoUrl() {
        if (receiptSettingsRepository == null) {
            return null;
        }
        try {
            return receiptSettingsRepository.findAll().stream()
                    .findFirst()
                    .map(ReceiptSettings::getLogoUrl)
                    .filter(value -> value != null && !value.isBlank())
                    .orElse(null);
        } catch (Exception exception) {
            log.debug("Unable to resolve logo URL for campaign creative", exception);
            return null;
        }
    }

    private void drawOfferBadge(Graphics2D graphics, Campaign campaign, int size, int margin, Color warmGold, boolean allowMarathiOverlay) {
        String badge = trimToNull(buildCreativeOfferBadge(campaign));
        if (!allowMarathiOverlay && containsDevanagari(badge)) {
            badge = buildEnglishOfferBadge(campaign);
        }
        if (badge == null) {
            return;
        }
        int diameter = Math.max(156, size / 5);
        int x = size - margin - diameter;
        int y = margin + Math.max(58, size / 18);
        graphics.setColor(new Color(117, 24, 29, 226));
        graphics.fill(new Ellipse2D.Double(x, y, diameter, diameter));
        graphics.setStroke(new java.awt.BasicStroke(Math.max(3f, size / 260f)));
        graphics.setColor(warmGold);
        graphics.draw(new Ellipse2D.Double(x + 6, y + 6, diameter - 12, diameter - 12));

        Font badgeFont = preferredFont(Font.BOLD, Math.max(28, size / 28), "Noto Sans Devanagari", "Nirmala UI", "SansSerif");
        List<String> lines = splitTextLines(badge.replace(" पर्यंत ", " ").replace(" up to ", " "), 12, 3);
        FontMetrics metrics = graphics.getFontMetrics(badgeFont);
        int lineHeight = metrics.getHeight();
        int textY = y + (diameter - (lineHeight * lines.size())) / 2 + metrics.getAscent();
        graphics.setFont(badgeFont);
        graphics.setColor(warmGold);
        for (String line : lines) {
            int textX = x + (diameter - metrics.stringWidth(line)) / 2;
            graphics.drawString(line, textX, textY);
            textY += lineHeight;
        }
    }

    private void drawRoundedTextBar(Graphics2D graphics,
                                    String text,
                                    Font font,
                                    Color background,
                                    Color foreground,
                                    int x,
                                    int y,
                                    int width) {
        FontMetrics metrics = graphics.getFontMetrics(font);
        int height = metrics.getHeight() + 24;
        graphics.setColor(background);
        graphics.fillRoundRect(x, y, width, height, height, height);
        graphics.setFont(font);
        graphics.setColor(foreground);
        String clipped = clipTextToWidth(text, metrics, width - 48);
        int textX = x + (width - metrics.stringWidth(clipped)) / 2;
        int textY = y + ((height - metrics.getHeight()) / 2) + metrics.getAscent();
        graphics.drawString(clipped, textX, textY);
    }

    private void drawWrappedText(Graphics2D graphics,
                                 String text,
                                 Font font,
                                 Color color,
                                 int x,
                                 int y,
                                 int width,
                                 int maxLines,
                                 TextAlign align) {
        String cleaned = defaultString(text, "").replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (cleaned.isBlank()) {
            return;
        }

        AttributedString attributedString = new AttributedString(cleaned);
        attributedString.addAttribute(TextAttribute.FONT, font);
        FontRenderContext renderContext = graphics.getFontRenderContext();
        LineBreakMeasurer measurer = new LineBreakMeasurer(attributedString.getIterator(), renderContext);
        int end = cleaned.length();
        float drawY = y;
        int lineCount = 0;
        graphics.setColor(color);
        while (measurer.getPosition() < end && lineCount < maxLines) {
            TextLayout layout = measurer.nextLayout(width);
            drawY += layout.getAscent();
            float drawX = switch (align) {
                case CENTER -> x + (width - layout.getAdvance()) / 2f;
                case RIGHT -> x + width - layout.getAdvance();
                case LEFT -> x;
            };
            layout.draw(graphics, drawX, drawY);
            drawY += layout.getDescent() + layout.getLeading() + (font.getSize2D() * 0.12f);
            lineCount++;
        }
    }

    private Font preferredFont(int style, int size, String... familyNames) {
        Set<String> availableFamilies = availableFontFamilies();
        for (String familyName : familyNames) {
            if (availableFamilies.contains(familyName.toLowerCase(Locale.ROOT))) {
                return new Font(familyName, style, size);
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    private boolean supportsDevanagariOverlay() {
        String sample = "मराठी ज्वेलरी कलेक्शन सूट";
        List<String> devanagariFamilies = List.of(
                "Noto Sans Devanagari",
                "Noto Serif Devanagari",
                "Nirmala UI",
                "Lohit Devanagari",
                "Mangal",
                "Kokila",
                "Aparajita"
        );
        Set<String> availableFamilies = availableFontFamilies();
        for (String familyName : devanagariFamilies) {
            if (!availableFamilies.contains(familyName.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Font font = new Font(familyName, Font.PLAIN, 28);
            if (font.canDisplayUpTo(sample) < 0) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDevanagari(String value) {
        if (value == null) {
            return false;
        }
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0x0900 && codePoint <= 0x097F);
    }

    private Set<String> availableFontFamilies() {
        Set<String> families = new HashSet<>();
        for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT)) {
            families.add(family.toLowerCase(Locale.ROOT));
        }
        return families;
    }

    private void applyImageRenderingHints(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private String buildCreativeImageHeadline(Campaign campaign,
                                              String productName,
                                              MarketingOccasionLibrary.Occasion festivalContext) {
        String language = resolveLanguage(campaign);
        String offerLine = buildOfferLine(campaign, festivalContext);
        if ("MARATHI".equals(language)) {
            if (isNoOfferCampaign(campaign)) {
                return ensureMarathiHeadlineQuality(
                        buildNoOfferMarathiImageHeadline(campaign, productName, festivalContext),
                        campaign,
                        festivalContext
                );
            }
            if (isOccasion(festivalContext, "mothers-day")) {
                return buildMarathiMothersDayOfferLine(campaign);
            }
            if (isOccasion(festivalContext, "wedding-season")) {
                return buildMarathiWeddingSeasonOfferLine(campaign);
            }
            String subject = defaultString(resolveShowcaseSubject(campaign, productName, festivalContext), "निवडक कलेक्शन");
            String headline;
            if (festivalContext != null) {
                String line = defaultString(offerLine, subject);
                headline = mentionsOccasion(line, festivalContext)
                        ? line
                        : "%s %s".formatted(marathiOccasionDative(festivalContext), line);
            } else {
                headline = defaultString(offerLine, subject + " आजच पाहा");
            }
            return ensureMarathiHeadlineQuality(headline, campaign, festivalContext);
        }
        if (isNoOfferCampaign(campaign)) {
            return buildEnglishImageHeadline(campaign, productName, festivalContext);
        }
        String subject = defaultString(resolveShowcaseSubject(campaign, productName, festivalContext), "curated collection");
        if (festivalContext != null) {
            return "%s picks for %s".formatted(defaultString(offerLine, subject), festivalContext.englishName());
        }
        return defaultString(offerLine, "Explore " + subject);
    }

    private String buildNoOfferMarathiImageHeadline(Campaign campaign,
                                                    String productName,
                                                    MarketingOccasionLibrary.Occasion festivalContext) {
        String subject = defaultString(resolveShowcaseSubject(campaign, productName, festivalContext), "निवडक कलेक्शन");
        return switch (campaignGoal(campaign)) {
            case "GREETING" -> festivalContext == null
                    ? "मनःपूर्वक शुभेच्छा"
                    : buildMarathiGreetingHeadline(festivalContext);
            case "QUOTE" -> "सौंदर्य छोट्या तपशीलांतून खुलतं";
            case "PRODUCT_STORY" -> "कलेक्शनची खास गोष्ट";
            default -> subject + " पाहा";
        };
    }

    private String buildMarathiGreetingHeadline(MarketingOccasionLibrary.Occasion festivalContext) {
        if (festivalContext == null) {
            return "मनःपूर्वक शुभेच्छा";
        }
        return switch (festivalContext.key()) {
            case "republic-day" -> "प्रजासत्ताक दिनाच्या हार्दिक शुभेच्छा";
            case "independence-day" -> "स्वातंत्र्य दिनाच्या हार्दिक शुभेच्छा";
            case "womens-day" -> "महिला दिनाच्या हार्दिक शुभेच्छा";
            case "mothers-day" -> "मातृ दिनाच्या हार्दिक शुभेच्छा";
            case "fathers-day" -> "पितृ दिनाच्या हार्दिक शुभेच्छा";
            case "maharashtra-day" -> "महाराष्ट्र दिनाच्या हार्दिक शुभेच्छा";
            default -> marathiOccasionPossessive(festivalContext) + " हार्दिक शुभेच्छा";
        };
    }

    private String buildEnglishGreetingHeadline(Campaign campaign,
                                                MarketingOccasionLibrary.Occasion festivalContext) {
        if (festivalContext != null) {
            return switch (festivalContext.key()) {
                case "womens-day" -> "Happy Women's Day";
                case "republic-day" -> "Happy Republic Day";
                case "independence-day" -> "Happy Independence Day";
                case "mothers-day" -> "Happy Mother's Day";
                case "fathers-day" -> "Happy Father's Day";
                case "maharashtra-day" -> "Happy Maharashtra Day";
                default -> "Happy " + festivalContext.englishName();
            };
        }
        return defaultString(normalizeOfferTitle(campaign.getCampaignName(), null), "Warm wishes");
    }

    private String buildEnglishImageHeadline(Campaign campaign,
                                             String productName,
                                             MarketingOccasionLibrary.Occasion festivalContext) {
        String subject = defaultString(normalizeOfferTitle(productName, festivalContext),
                defaultString(normalizeOfferTitle(campaign.getOfferProduct(), festivalContext),
                        defaultString(normalizeOfferTitle(campaign.getOfferTitle(), festivalContext), "curated collection")));
        if (isNoOfferCampaign(campaign)) {
            return switch (campaignGoal(campaign)) {
                case "GREETING" -> festivalContext == null
                        ? defaultString(normalizeOfferTitle(campaign.getCampaignName(), festivalContext), "Warm wishes")
                        : buildEnglishGreetingHeadline(campaign, festivalContext);
                case "QUOTE" -> defaultString(normalizeOfferTitle(campaign.getCampaignName(), festivalContext), "A beautiful thought for today");
                case "PRODUCT_STORY" -> "The story behind " + subject;
                default -> "Explore " + subject;
            };
        }
        return buildEnglishOfferBadge(campaign);
    }

    private String buildEnglishImageCta(Campaign campaign, MarketingPlatform platform) {
        if (isNoOfferCampaign(campaign)) {
            return switch (campaignGoal(campaign)) {
                case "GREETING", "QUOTE" -> "Visit now";
                case "PRODUCT_STORY" -> "Explore the story";
                default -> "Explore collection";
            };
        }
        return platform == MarketingPlatform.WHATSAPP ? "Shop now" : "Explore collection";
    }

    private String clipTextToWidth(String text, FontMetrics metrics, int maxWidth) {
        String cleaned = defaultString(text, "");
        if (metrics.stringWidth(cleaned) <= maxWidth) {
            return cleaned;
        }
        String ellipsis = "...";
        while (!cleaned.isBlank() && metrics.stringWidth(cleaned + ellipsis) > maxWidth) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned + ellipsis;
    }

    private enum TextAlign {
        LEFT,
        CENTER,
        RIGHT
    }

    private GeneratedImagePayload generateOpenAiImagePayload(String imagePrompt) throws IOException, InterruptedException {
        if (isBlank(marketingProperties.getAi().getApiKey()) || isBlank(marketingProperties.getAi().getImageModel())) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", defaultString(marketingProperties.getAi().getImageModel(), "gpt-image-1.5"));
        payload.put("prompt", imagePrompt);
        payload.put("size", defaultString(marketingProperties.getAi().getImageSize(), "1024x1024"));
        payload.put("quality", defaultString(marketingProperties.getAi().getImageQuality(), "medium"));
        payload.put("output_format", "png");

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/images/generations"))
                .header("authorization", "Bearer " + marketingProperties.getAi().getApiKey().trim())
                .header("content-type", "application/json")
                .timeout(CAMPAIGN_IMAGE_OPENAI_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("OpenAI image generation failed with status " + response.statusCode() + ": " + extractApiErrorMessage(response.body()));
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode imageNode = root.path("data").path(0);
        String base64Image = trimToNull(imageNode.path("b64_json").asText(""));
        if (base64Image == null) {
            String remoteUrl = trimToNull(imageNode.path("url").asText(""));
            if (remoteUrl == null) {
                throw new IOException("OpenAI image generation returned no image data");
            }
            HttpResponse<byte[]> imageResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create(remoteUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            if (imageResponse.statusCode() >= 400 || imageResponse.body() == null || imageResponse.body().length == 0) {
                throw new IOException("OpenAI image download failed with status " + imageResponse.statusCode());
            }
            String contentType = defaultString(imageResponse.headers().firstValue("content-type").orElse("image/png"), "image/png");
            contentType = contentType.split(";", 2)[0].trim();
            if (!contentType.startsWith("image/")) {
                contentType = "image/png";
            }
            return new GeneratedImagePayload(imageResponse.body(), contentType, remoteUrl);
        }
        return new GeneratedImagePayload(Base64.getDecoder().decode(base64Image), "image/png", null);
    }

    private String extractApiErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = trimToNull(root.path("error").path("message").asText(""));
            return defaultString(message, "Unknown API error");
        } catch (IOException ignored) {
            return "Unknown API error";
        }
    }

    private String buildUserPrompt(Campaign campaign,
                                   String shopName,
                                   String categoryName,
                                   String productName,
                                   MarketingPlatform platform,
                                   MarketingOccasionLibrary.Occasion festivalContext) {
        return """
                Campaign name: %s
                Shop name: %s
                Platform: %s
                Campaign type: %s
                Campaign goal: %s
                Offer mode: %s
                Category: %s
                Product: %s
                Offer title: %s
                Coupon code: %s
                Discount type: %s
                Discount value: %s
                Language: %s
                Tone: %s
                Start date: %s
                End date: %s
                Festival context inferred from the title: %s
                Campaign goal instruction: %s

                Generate:
                - Instagram: short premium caption, 8-15 hashtags, luxury CTA
                - Facebook: slightly longer emotional/festival caption
                - WhatsApp: short direct message for the template body line only, no full message body and no hashtag overload
                - If language is MARATHI, keep caption, CTA, and selling line fully in Marathi
                - If a festival is inferred, mention it naturally and accurately instead of generic festive wording
                - If offer mode is NONE and discount type is NONE, create a greeting, quote, product story, or awareness campaign; do not mention discount, sale, coupon, or offer
                - If a coupon code is present, mention the exact coupon code once and do not invent another code
                - Use local Maharashtra-friendly retail phrasing for Marathi campaigns
                - Caption/message must start with an eye-catching hook or quote-style sentence, then the offer, then a simple action line
                - Caption/message should focus on the campaign message and offer; do not repeat the shop name unless it is required for clarity
                - CTA must invite the customer to visit now; never say "Share this message" or "हा संदेश शेअर करा"
                - Avoid repeating campaign name or offer title as-is; turn it into attractive ad copy
                - For Marathi, use phrases like ज्वेलरी, नेकलेस, बांगड्या, इयररिंग्स, गिफ्ट, सौंदर्य, कलेक्शन when relevant
                - Avoid precious-metal-shop wording
                - Image prompt must be platform-neutral and suitable for one square creative reused on Instagram, Facebook, and WhatsApp
                - Image prompt must ask for a clean product/background visual only; all text will be overlaid later by the website
                - Image prompt must not ask to write the shop name, brand name, or social CTA on the image
                - Image prompt must explicitly say: no text, no letters, no numbers, no logo, no watermark, no discount badge, no typography
                - Do not invent fake logos, seals, brand marks, watermarks, placeholder text, or extra unreadable label blocks
                """.formatted(
                safe(campaign.getCampaignName()),
                safe(shopName),
                platform.name(),
                safe(campaign.getCampaignType() != null ? campaign.getCampaignType().name() : ""),
                safe(campaign.getCampaignGoal()),
                safe(campaign.getOfferMode()),
                safe(categoryName),
                safe(productName),
                safe(campaign.getOfferTitle()),
                safe(campaign.getCouponCode()),
                safe(campaign.getDiscountType() != null ? campaign.getDiscountType().name() : ""),
                safe(campaign.getDiscountValue() != null ? campaign.getDiscountValue().toPlainString() : ""),
                safe(campaign.getLanguage() != null ? campaign.getLanguage().name() : ""),
                safe(campaign.getTone() != null ? campaign.getTone().name() : ""),
                safe(campaign.getStartDate() != null ? campaign.getStartDate().toString() : ""),
                safe(campaign.getEndDate() != null ? campaign.getEndDate().toString() : ""),
                describeFestivalContext(festivalContext),
                campaignGoalInstruction(campaign)
        );
    }

    private String campaignGoalInstruction(Campaign campaign) {
        return switch (campaignGoal(campaign)) {
            case "OFFER" -> "Create a conversion campaign around the real offer/coupon. Mention discount only if discount fields are present.";
            case "GREETING" -> "Create a warm greeting campaign. Lead with wishes or celebration feeling; avoid sales pressure and avoid discount language unless an offer is attached.";
            case "QUOTE" -> "Create a quote or congratulation campaign. Lead with a polished short quote/message; keep it meaningful and brand-safe; avoid forced product selling unless a product is selected.";
            case "PRODUCT_STORY" -> "Create a product-story campaign. Explain why the selected product/category is useful, giftable, stylish, or occasion-ready.";
            default -> "Create a brand-awareness campaign about trust, selection, easy shopping, and customer confidence.";
        };
    }

    private String buildMockCaption(Campaign campaign,
                                    String shopName,
                                    String productName,
                                    MarketingPlatform platform,
                                    MarketingOccasionLibrary.Occasion festivalContext) {
        String productOrCampaign = resolveShowcaseSubject(campaign, productName, festivalContext);
        String offerLine = buildOfferLine(campaign, festivalContext);
        String language = resolveLanguage(campaign);
        if (isNoOfferCampaign(campaign)) {
            return buildNoOfferGoalCaption(campaign, shopName, productOrCampaign, platform, festivalContext);
        }
        if ("MARATHI".equals(language)) {
            if (isOccasion(festivalContext, "mothers-day")) {
                return buildMarathiMothersDayCaption(campaign, shopName, platform);
            }
            if (isOccasion(festivalContext, "wedding-season")) {
                return buildMarathiWeddingSeasonCaption(campaign, shopName, platform);
            }
            return switch (platform) {
                case INSTAGRAM -> buildMarathiInstagramCaption(campaign, productOrCampaign, offerLine, festivalContext);
                case FACEBOOK -> buildMarathiFacebookCaption(campaign, shopName, productOrCampaign, offerLine, festivalContext);
                case WHATSAPP -> buildMockWhatsAppLine(campaign, productOrCampaign, offerLine, festivalContext);
            };
        }
        return switch (platform) {
            case INSTAGRAM -> buildEnglishInstagramCaption(campaign, shopName, productOrCampaign, offerLine, festivalContext);
            case FACEBOOK -> buildEnglishFacebookCaption(campaign, shopName, productOrCampaign, offerLine, festivalContext);
            case WHATSAPP -> buildMockWhatsAppLine(campaign, productOrCampaign, offerLine, festivalContext);
        };
    }

    private String buildNoOfferGoalCaption(Campaign campaign,
                                           String shopName,
                                           String productOrCampaign,
                                           MarketingPlatform platform,
                                           MarketingOccasionLibrary.Occasion festivalContext) {
        String goal = campaignGoal(campaign);
        String language = resolveLanguage(campaign);
        String subject = defaultString(productOrCampaign, "MARATHI".equals(language) ? "आमचे निवडक डिझाइन्स" : "our curated collection");
        String theme = defaultString(normalizeOfferTitle(campaign.getCampaignName(), festivalContext),
                festivalContext == null ? subject : festivalContext.englishName());
        if ("MARATHI".equals(language)) {
            String festival = festivalContext == null ? "आजच्या सुंदर क्षणांसाठी" : festivalDisplayName(festivalContext) + " निमित्त";
            return switch (goal) {
                case "GREETING" -> "%s मनःपूर्वक शुभेच्छा. %s मधून तुमचा खास लुक अधिक सुंदर करा."
                        .formatted(festival, subject);
                case "QUOTE" -> "\"सौंदर्य छोट्या तपशीलांतून खुलतं.\" %s - %s तुमच्यासाठी खास निवड."
                        .formatted(theme, subject);
                case "PRODUCT_STORY" -> "%s मधील प्रत्येक डिझाइन तुमच्या रोजच्या आणि खास क्षणांना नवा स्पर्श देते. कलेक्शन शांतपणे पाहा आणि आवडती निवड करा."
                        .formatted(subject);
                default -> "सुंदर निवड, सहज खरेदी आणि विश्वासू सेवा. %s आजच पाहा."
                        .formatted(subject);
            };
        }
        if ("HINGLISH".equals(language)) {
            return switch (goal) {
                case "GREETING" -> "%s ki heartfelt wishes. %s se apna look aur special banao."
                        .formatted(festivalContext == null ? theme : festivalContext.englishName(), subject);
                case "QUOTE" -> "\"Beauty lives in the little details.\" %s for your everyday confidence."
                        .formatted(subject);
                case "PRODUCT_STORY" -> "%s is styled for easy gifting, daily wear, and celebration-ready looks. Explore at your pace."
                        .formatted(subject);
                default -> "Curated retail picks, simple shopping, and trusted service for every occasion.";
            };
        }
        return switch (goal) {
            case "GREETING" -> "Warm wishes for %s. Explore %s made for beautiful everyday and festive moments."
                    .formatted(festivalContext == null ? theme : festivalContext.englishName(), subject);
            case "QUOTE" -> "\"Beauty lives in the little details.\" %s for your everyday confidence."
                    .formatted(subject);
            case "PRODUCT_STORY" -> "%s is styled for easy gifting, daily wear, and celebration-ready looks. Explore at your pace."
                    .formatted(subject);
            default -> "Curated retail picks, simple shopping, and trusted service for every occasion.";
        };
    }

    private String buildMockHashtags(Campaign campaign, String categoryName, MarketingPlatform platform, MarketingOccasionLibrary.Occasion festivalContext) {
        if (platform == MarketingPlatform.WHATSAPP) {
            return "";
        }
        String normalizedCategory = defaultString(categoryName, "RetailStyle").replaceAll("[^A-Za-z0-9]", "");
        String language = resolveLanguage(campaign);
        if ("MARATHI".equals(language)) {
            if (isOccasion(festivalContext, "mothers-day")) {
                return "#मातृदिन #आईसाठीगिफ्ट #ज्वेलरीकलेक्शन #नेकलेस #बांगड्या #इयररिंग्स #सौंदर्य #KrishnaiPearl";
            }
            if (isOccasion(festivalContext, "wedding-season")) {
                return "#लग्नसराई #ब्रायडलकलेक्शन #ज्वेलरीकलेक्शन #नेकलेस #बांगड्या #इयररिंग्स #गिफ्टकलेक्शन #KrishnaiPearl";
            }
            String festivalTag = festivalContext == null ? "#सणासुदीचीऑफर" : festivalContext.marathiHashtag();
            return "%s #महाराष्ट्रीयनस्टाईल #खासऑफर #महिलांसाठी #उत्सवीकलेक्शन #%s #ShopLocal #PremiumCollection"
                    .formatted(festivalTag, normalizedCategory.isBlank() ? "RetailStyle" : normalizedCategory);
        }
        return "#%s #LadiesCollection #RetailFinds #FestiveStyle #ShopLocal #PremiumSelection #NewArrival #GiftReady"
                .formatted(normalizedCategory)
                + (festivalContext == null ? "" : " " + festivalContext.englishHashtag());
    }

    private String buildMockCta(Campaign campaign, MarketingPlatform platform, MarketingOccasionLibrary.Occasion festivalContext) {
        String language = resolveLanguage(campaign);
        if (isNoOfferCampaign(campaign)) {
            String goal = campaignGoal(campaign);
            if ("MARATHI".equals(language)) {
                return switch (goal) {
                    case "GREETING", "QUOTE" -> "आता भेट द्या";
                    case "PRODUCT_STORY" -> "कलेक्शनची गोष्ट पाहा";
                    default -> "कलेक्शन पाहा";
                };
            }
            if ("HINGLISH".equals(language)) {
                return switch (goal) {
                    case "GREETING", "QUOTE" -> "Visit now";
                    case "PRODUCT_STORY" -> "Explore the story";
                    default -> "Explore collection";
                };
            }
            return switch (goal) {
                case "GREETING", "QUOTE" -> "Visit now";
                case "PRODUCT_STORY" -> "Explore the story";
                default -> "Explore collection";
            };
        }
        if ("MARATHI".equals(language)) {
            if (isOccasion(festivalContext, "mothers-day")) {
                return platform == MarketingPlatform.WHATSAPP ? "आईसाठी भेट निवडा" : "आईसाठी कलेक्शन पाहा";
            }
            if (isOccasion(festivalContext, "wedding-season")) {
                return "लग्नसराई कलेक्शन पाहा";
            }
            return switch (platform) {
                case INSTAGRAM -> "आता कलेक्शन पाहा";
                case FACEBOOK -> defaultString(festivalContext == null ? null : festivalContext.marathiVisitCta(), "आजच दुकानात किंवा वेबसाईटवर भेट द्या");
                case WHATSAPP -> "आता खरेदी करा";
            };
        }
        return switch (platform) {
            case INSTAGRAM -> "Tap to explore the collection";
            case FACEBOOK -> "Visit the shop or website today";
            case WHATSAPP -> "Shop now";
        };
    }

    private String buildMarathiMothersDayCaption(Campaign campaign,
                                                 String shopName,
                                                 MarketingPlatform platform) {
        String offerLine = buildMarathiMothersDayOfferLine(campaign);
        return switch (platform) {
            case INSTAGRAM -> "आईच्या प्रेमासाठी एक सुंदर भेट. %s. तुमची आवडती निवड आजच पाहा."
                    .formatted(offerLine);
            case FACEBOOK -> "आईने नेहमी आपली काळजी घेतली; आता तिच्यासाठी एक सुंदर भेट निवडा. %s. निवडक कलेक्शनमधून तिच्या आवडीची भेट आजच निवडा."
                    .formatted(offerLine);
            case WHATSAPP -> "आईसाठी एक सुंदर भेट निवडा. %s. आजच कलेक्शन पाहा."
                    .formatted(offerLine);
        };
    }

    private String buildMarathiWeddingSeasonCaption(Campaign campaign,
                                                    String shopName,
                                                    MarketingPlatform platform) {
        String offerLine = buildMarathiWeddingSeasonOfferLine(campaign);
        return switch (platform) {
            case INSTAGRAM -> "लग्नसराईचा खास लुक पूर्ण करा. %s. नेकलेस, बांगड्या आणि इयररिंग्समधून तुमची आवडती निवड आजच करा."
                    .formatted(offerLine);
            case FACEBOOK -> "लग्नाचा खास दिवस अधिक सुंदर बनवा. %s. ब्रायडल ज्वेलरी आणि गिफ्ट कलेक्शनमधून तुमच्या आवडीची निवड करा."
                    .formatted(offerLine);
            case WHATSAPP -> "%s. तुमच्या खास दिवसासाठी सुंदर ज्वेलरी आणि गिफ्ट कलेक्शन आजच पाहा."
                    .formatted(offerLine);
        };
    }

    private String buildSharedImagePrompt(Campaign campaign,
                                          String shopName,
                                          String categoryName,
                                          String productName,
                                          MarketingOccasionLibrary.Occasion festivalContext,
                                          String visualSeed) {
        String occasionName = festivalContext == null ? "seasonal retail" : festivalContext.englishName();
        String subject = defaultString(productName, defaultString(categoryName, "jewellery, cosmetics, gifting products"));
        String resolvedVisualSeed = defaultString(visualSeed, UUID.randomUUID().toString());
        int sceneVariant = visualVariant(campaign, festivalContext, resolvedVisualSeed, 5);
        int compositionVariant = visualVariant(campaign, festivalContext, resolvedVisualSeed + "|composition", 4);
        String scenePrompt = buildFestivalScenePrompt(subject, festivalContext, sceneVariant);
        String variationPrompt = buildVisualVariationPrompt(festivalContext, compositionVariant);
        return "Premium square 1:1 social-commerce campaign background for an Indian retail campaign. Occasion: %s. Product focus: %s. Tone: %s. Campaign goal: %s. Goal visual direction: %s. Visual refresh seed: %s, for composition only, do not display it. Scene direction: %s. Composition variation: %s. Make this regeneration visually fresh and noticeably different from earlier drafts by changing the camera angle, layout, props, background depth, and product placement. Make the image clearly relatable to the occasion, with Indian retail context and natural festival props, not just a product catalogue display. Show jewellery, cosmetics, or gift products as part of the festival story. Use a refined palette that matches the occasion, with realistic light, depth, and premium styling. Leave clean negative space at the top and bottom for later text overlay. Avoid repeating the same necklace-on-silk flat lay, generic jewellery tray, plain studio product display, same gift-box handoff pose, or unrelated decorative background. If the campaign goal is GREETING or QUOTE, make the image feel like a greeting card or meaningful social post background, not a sale poster. No text, no letters, no numbers, no logo, no watermark, no discount badge, no typography, no fake brand mark, no placeholder label blocks. Do not include readable words on banners, cards, posters, balloons, shop signs, wall decor, or product labels; all copy will be added later by the website. Do not use wording or visuals that imply a precious-metal shop."
                .formatted(
                        occasionName,
                        subject,
                        defaultString(campaign.getTone() != null ? campaign.getTone().name().toLowerCase(Locale.ROOT) : null, "premium"),
                        campaignGoal(campaign),
                        campaignGoalVisualDirection(campaign),
                        visualSeedLabel(resolvedVisualSeed),
                        scenePrompt,
                        variationPrompt
                );
    }

    private String campaignGoalVisualDirection(Campaign campaign) {
        return switch (campaignGoal(campaign)) {
            case "OFFER" -> "clear retail offer mood with product prominence, clean space for discount and CTA overlays";
            case "GREETING" -> "warm occasion greeting mood with human emotion, festival props, and soft negative space for wishes";
            case "QUOTE" -> "minimal premium composition with meaningful atmosphere and calm negative space for a quote";
            case "PRODUCT_STORY" -> "show the product/category in use, being selected, gifted, styled, or prepared for an occasion";
            default -> "brand-awareness lifestyle scene showing trust, retail warmth, and curated selection";
        };
    }

    private String buildFestivalScenePrompt(String subject,
                                            MarketingOccasionLibrary.Occasion festivalContext,
                                            int variant) {
        if (festivalContext == null) {
            return pickVariant(variant,
                    "a fresh retail lifestyle scene with gift-ready packaging, customer browsing mood, and %s placed naturally in the scene".formatted(subject),
                    "a premium shop-counter moment with a customer choosing a gift, soft retail lighting, and %s in the foreground".formatted(subject),
                    "a gift-wrapping table scene with ribbon, tissue paper, flowers, and %s arranged as a thoughtful purchase".formatted(subject),
                    "a dressing-table styling scene with cosmetics, jewellery, mirror light, and %s integrated naturally".formatted(subject),
                    "a warm home-gifting scene with festive decor, shopping bag, gift card without readable text, and %s as the hero".formatted(subject));
        }
        return switch (festivalContext.key()) {
            case "mothers-day" -> pickVariant(variant,
                    "a warm Mother's Day gifting moment: a daughter handing a wrapped gift box to her mother at home, flowers, greeting card without readable text, soft morning light, a small jewellery box or beauty gift visible, emotional and premium",
                    "a mother at a dressing table receiving a jewellery or cosmetics gift, saree drape, flowers, mirror glow, tender family atmosphere",
                    "a boutique gift-selection moment where a daughter helps her mother choose a pearl-style necklace or beauty gift, elegant counter, soft retail lighting",
                    "a Mother's Day breakfast-and-gift scene with fresh flowers, wrapped box, perfume, lipstick, and a small jewellery case, warm home setting",
                    "a family celebration preparation scene with mother in saree, daughter holding a gift bag, subtle jewellery and cosmetics on the side, premium emotional mood");
            case "wedding-season" -> pickVariant(variant,
                    "a Maharashtrian wedding preparation scene: bridal trousseau, saree fabric, mehendi hands, marigold flowers, soft mandap decor in the background, statement jewellery and gift box arranged as part of the bridal story",
                    "a bride getting ready near a mirror with Paithani-inspired fabric, bangles, earrings, cosmetics, and warm wedding lights",
                    "a haldi-mehendi table scene with marigold petals, bangles, pearl-style jewellery, gift packaging, and festive wedding decor",
                    "a wedding gift-selection moment in a premium retail shop, bridal accessories and cosmetics presented to a family member",
                    "an editorial bridal trousseau scene with folded saree, flowers, invitation card without readable text, jewellery box, and makeup essentials");
            case "diwali" -> pickVariant(variant,
                    "a Diwali gifting scene: diyas, rangoli edge, festive gift boxes, warm lantern light, jewellery or beauty products styled as gifts for family celebrations",
                    "a festive home entrance with diyas, rangoli colors, marigold toran, and gift-ready jewellery or cosmetics in the foreground",
                    "a Diwali evening dressing-table scene with mirror lights, lipstick, earrings, bangles, and soft lamp glow",
                    "a family-gifting corner with wrapped boxes, diyas, flowers, and a premium jewellery or beauty gift opened halfway",
                    "a shop display prepared for Diwali shopping with lamps, rangoli-inspired props, and curated gift products");
            case "gudi-padwa" -> pickVariant(variant,
                    "a Gudi Padwa new-year scene: festive home entrance, gudi, mango leaves, saffron silk, fresh flowers, jewellery or beauty gift placed as an auspicious new purchase",
                    "a Maharashtrian festive dressing scene with Paithani-inspired fabric, gudi in soft background, bangles, earrings, and cosmetics",
                    "a new-year gifting counter with mango leaves, flowers, saffron accents, and a premium gift box containing jewellery or beauty products",
                    "a bright morning home scene with rangoli edge, gudi, fresh flowers, and a jewellery box arranged for शुभ खरेदी",
                    "a shop-window festival setup with saffron-green accents, mango leaves, and retail gifts for Gudi Padwa");
            case "akshaya-tritiya" -> pickVariant(variant,
                    "an Akshaya Tritiya auspicious shopping scene: puja thali, turmeric, rice, flowers, temple-bell inspired decor, elegant jewellery box as a festive purchase",
                    "a bright auspicious home-buying scene with flowers, puja plate, folded silk fabric, and a gift-ready jewellery or cosmetics product",
                    "a premium retail counter for Akshaya Tritiya with festive flowers, rice grains, turmeric, and curated jewellery pieces",
                    "a family shopping moment with hands opening a jewellery gift box beside a puja thali and warm festive light",
                    "an elegant still life with auspicious yellow flowers, silk cloth, beauty gift, and jewellery box, avoiding precious-metal-shop cues");
            case "makar-sankranti" -> pickVariant(variant,
                    "a Makar Sankranti haldi-kumkum scene: tilgul sweets, black-and-gold festive styling, turmeric-kumkum thali, bangles or earrings as thoughtful gifts",
                    "a women-focused Sankranti gathering with haldi-kumkum plate, tilgul, black saree accents, and gift-ready cosmetics or jewellery",
                    "a festive gift tray with sesame sweets, flowers, bangles, earrings, and a beauty product, warm community celebration mood",
                    "a dressing-table scene with black-and-gold styling, kajal, lipstick, bangles, and Sankranti props",
                    "a premium retail gift counter themed for haldi-kumkum return gifts, with jewellery and cosmetics arranged naturally");
            case "raksha-bandhan" -> pickVariant(variant,
                    "a Raksha Bandhan gifting scene: rakhi thali, sibling gift box, warm family setting, jewellery or beauty gift shown as a premium surprise",
                    "a close-up of a rakhi thali and wrapped gift box with earrings, cosmetics, flowers, and festive home lighting",
                    "a sister opening a jewellery or beauty gift after rakhi, warm family background, no readable text",
                    "a premium shop-counter scene with rakhi-themed gifting products and elegant retail packaging",
                    "a festive home table with rakhi, sweets, flowers, gift bag, and jewellery or beauty products as the hero");
            case "ganesh-chaturthi" -> pickVariant(variant,
                    "a Ganesh Chaturthi festive home scene: marigold toran, modak plate, warm festive decor, family shopping mood, jewellery or beauty products as celebration-ready gifts",
                    "a devotional home setup with marigold flowers, modak, soft festive lights, and gift-ready jewellery or cosmetics in the foreground",
                    "a festive dressing scene before Ganesh Chaturthi visit, with bangles, earrings, cosmetics, and marigold accents",
                    "a shop display with modak-inspired decor, flowers, and curated gifts for Ganesh Chaturthi celebrations",
                    "a warm family celebration table with sweets, flowers, gift box, and premium retail products");
            case "navratri" -> pickVariant(variant,
                    "a Navratri celebration scene: colorful dupattas, dandiya sticks, festive lights, makeup and jewellery prepared for a garba night",
                    "a garba-night dressing table with colorful fabric, bangles, earrings, lipstick, kajal, and dandiya sticks",
                    "a festive shop display with Navratri colors, mirror-work fabric, jewellery, and cosmetics for celebration looks",
                    "a young woman preparing her festive look with bangles and cosmetics, dandiya lights in soft background",
                    "an editorial Navratri flat scene with bright dupattas, flowers, gift packaging, jewellery, and makeup essentials");
            case "dussehra" -> pickVariant(variant,
                    "a Dussehra auspicious buying scene: marigold flowers, festive entrance decor, gold-toned light, gift-ready jewellery or beauty products arranged for शुभ खरेदी",
                    "a home entrance with marigold toran, apta leaves, gift box, and premium jewellery or cosmetics for festive shopping",
                    "a shop-counter Dussehra scene with flowers, gift packaging, and a family choosing retail gifts",
                    "a dressing-table festive scene with saree fabric, bangles, cosmetics, marigold petals, and warm auspicious light",
                    "an elegant Dussehra gift tray with flowers, leaves, sweets, and curated jewellery or beauty products");
            case "holi", "rang-panchami" -> pickVariant(variant,
                    "a colorful Holi celebration scene: flower petals, bright gulal bowls, skincare or cosmetics with festive jewellery accents, joyful but elegant",
                    "a beauty gifting scene with gulal bowls, flower petals, skincare, lipstick, and playful festive colors",
                    "a Holi-ready styling table with waterproof makeup, earrings, flowers, and soft colorful powder accents",
                    "a bright home celebration corner with flowers, color bowls, wrapped gift, and cosmetics or jewellery as the hero",
                    "an editorial color-splash scene using dry gulal, petals, gift packaging, and premium retail products");
            case "womens-day" -> pickVariant(variant,
                    "a Women's Day self-gifting scene: elegant dressing table, confident styling mood, beauty products and jewellery prepared as a personal treat",
                    "a premium self-care table with cosmetics, jewellery, flowers, and an empowering gift box, no readable text",
                    "a women friends gifting moment with beauty and jewellery products on a cafe-style table, warm light",
                    "a boutique styling corner with mirror, jewellery, makeup, and flowers for Women's Day",
                    "a clean editorial self-gift composition with perfume, lipstick, earrings, bangles, and soft feminine colors");
            case "maharashtra-day" -> pickVariant(variant,
                    "a Maharashtra Day regional style scene: saffron and green festive accents, Paithani-inspired fabric, local pride decor, jewellery and beauty products styled for Maharashtrian elegance",
                    "a Maharashtrian styling table with Paithani-inspired silk, nath-style jewellery cues, bangles, and cosmetics",
                    "a regional festive shop display with saffron-green accents, flowers, and curated retail gifts",
                    "a home celebration setup with rangoli edge, flowers, folded saree, and jewellery or cosmetics in foreground",
                    "an editorial Maharashtrian elegance scene with local fabric, bangles, earrings, and beauty products");
            case "valentines-day" -> pickVariant(variant,
                    "a Valentine's Day gifting scene: rose petals, handwritten gift card without readable text, elegant jewellery or beauty gift box, romantic premium lighting",
                    "a romantic gift table with roses, perfume, lipstick, earrings, and a wrapped box, soft warm light",
                    "a couple-gifting close-up with hands exchanging a small jewellery or beauty gift, no readable text",
                    "a premium boutique Valentine's display with rose accents, gift packaging, jewellery and cosmetics",
                    "an editorial red-and-ivory gifting scene with flowers, perfume, jewellery box, and cosmetics");
            case "vat-pournima", "hartalika-teej", "karwa-chauth" -> pickVariant(variant,
                    "a traditional married-women festive scene: saree, mehendi, bangles, mangalsutra-inspired styling, flowers and devotional decor, premium but respectful",
                    "a puja preparation table with flowers, thali, bangles, earrings, cosmetics, and saree fabric",
                    "a woman getting ready for the traditional occasion, mirror light, bangles, cosmetics, flowers, and elegant jewellery cues",
                    "a respectful home festive scene with thali, flowers, gift box, and jewellery or beauty products as celebration-ready items",
                    "a premium retail gifting setup for traditional celebrations with saree fabric, bangles, flowers, and beauty products");
            default -> "an occasion-specific Indian festival lifestyle scene using these cues: %s, with %s placed naturally as part of gifting or festive dressing"
                    .formatted(festivalContext.visualHints(), subject);
        };
    }

    private String buildVisualVariationPrompt(MarketingOccasionLibrary.Occasion festivalContext, int variant) {
        String occasion = festivalContext == null ? "seasonal retail" : festivalContext.englishName();
        return switch (variant) {
            case 1 -> "lifestyle close-up with hands, gift box, and festival props; shallow depth of field; product is visible but not isolated";
            case 2 -> "wide festive home or shop-corner scene with clear occasion props for %s; product appears in the foreground with room for overlay text".formatted(occasion);
            case 3 -> "editorial social-media composition with diagonal fabric, flowers, gift packaging, and occasion-specific props; avoid central catalogue symmetry";
            default -> "premium realistic scene with a human gifting or getting-ready cue, festival props, and products integrated into the moment";
        };
    }

    private int visualVariant(Campaign campaign,
                              MarketingOccasionLibrary.Occasion festivalContext,
                              String visualSeed,
                              int count) {
        if (count <= 1) {
            return 0;
        }
        String seed = safe(campaign.getId() == null ? null : campaign.getId().toString()) + "|"
                + safe(campaign.getCampaignName()) + "|"
                + safe(campaign.getOfferTitle()) + "|"
                + safe(festivalContext == null ? null : festivalContext.key()) + "|"
                + safe(visualSeed);
        return Math.floorMod(seed.hashCode(), count);
    }

    private String visualSeedLabel(String visualSeed) {
        String cleaned = defaultString(visualSeed, UUID.randomUUID().toString()).replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.isBlank()) {
            return "fresh";
        }
        return cleaned.length() > 10 ? cleaned.substring(0, 10) : cleaned;
    }

    private String pickVariant(int variant, String... options) {
        if (options == null || options.length == 0) {
            return "";
        }
        return options[Math.floorMod(variant, options.length)];
    }

    private String buildOfferLine(Campaign campaign, MarketingOccasionLibrary.Occasion festivalContext) {
        String language = resolveLanguage(campaign);
        if (campaign.getDiscountType() == null || campaign.getDiscountType().name().equals("NONE")) {
            String cleanedOfferTitle = normalizeOfferTitle(campaign.getOfferTitle(), festivalContext);
            String noDiscountLine = defaultString(cleanedOfferTitle, "MARATHI".equals(language)
                    ? defaultString(festivalContext == null ? null : festivalDisplayName(festivalContext) + "च्या शुभेच्छा", "खास निवड")
                    : defaultString(festivalContext == null ? null : festivalContext.englishName() + " wishes", "Special picks"));
            return appendCouponSuffix(campaign, noDiscountLine, language);
        }
        String offerSubject = resolveOfferSubject(campaign, festivalContext, language);
        BigDecimal value = campaign.getDiscountValue() == null ? BigDecimal.ZERO : campaign.getDiscountValue();
        if ("MARATHI".equals(language)) {
            String line = switch (campaign.getDiscountType()) {
                case PERCENTAGE -> "%sवर %s%% पर्यंत सूट".formatted(marathiDiscountObject(offerSubject, festivalContext), value.stripTrailingZeros().toPlainString());
                case FLAT -> "%sवर ₹%s पर्यंत सूट".formatted(marathiDiscountObject(offerSubject, festivalContext), value.stripTrailingZeros().toPlainString());
                case NONE -> offerSubject;
            };
            return appendCouponSuffix(campaign, line, language);
        }
        if ("HINGLISH".equals(language)) {
            String line = switch (campaign.getDiscountType()) {
                case PERCENTAGE -> "%s par %s%% tak off".formatted(offerSubject, value.stripTrailingZeros().toPlainString());
                case FLAT -> "%s par flat %s off".formatted(offerSubject, value.stripTrailingZeros().toPlainString());
                case NONE -> offerSubject;
            };
            return appendCouponSuffix(campaign, line, language);
        }
        String line = switch (campaign.getDiscountType()) {
            case PERCENTAGE -> "%s - save %s%%".formatted(offerSubject, value.stripTrailingZeros().toPlainString());
            case FLAT -> "%s - flat %s off".formatted(offerSubject, value.stripTrailingZeros().toPlainString());
            case NONE -> offerSubject;
        };
        return appendCouponSuffix(campaign, line, language);
    }

    private String appendCouponSuffix(Campaign campaign, String line, String language) {
        String coupon = trimToNull(campaign.getCouponCode());
        if (coupon == null) {
            return line;
        }
        if ("MARATHI".equals(language)) {
            return line + " - कूपन कोड " + coupon;
        }
        if ("HINGLISH".equals(language)) {
            return line + " - coupon code " + coupon;
        }
        return line + " - use code " + coupon;
    }

    private String buildMarathiMothersDayOfferLine(Campaign campaign) {
        if (campaign.getDiscountType() == null || campaign.getDiscountType().name().equals("NONE")) {
            return appendCouponSuffix(campaign, "आईसाठी खास भेटवस्तू", "MARATHI");
        }
        BigDecimal value = campaign.getDiscountValue() == null ? BigDecimal.ZERO : campaign.getDiscountValue();
        String line = switch (campaign.getDiscountType()) {
            case PERCENTAGE -> "आईसाठी खास भेटवस्तूंवर %s%% पर्यंत सूट".formatted(value.stripTrailingZeros().toPlainString());
            case FLAT -> "आईसाठी खास भेटवस्तूंवर ₹%s पर्यंत सूट".formatted(value.stripTrailingZeros().toPlainString());
            case NONE -> "आईसाठी खास भेटवस्तू";
        };
        return appendCouponSuffix(campaign, line, "MARATHI");
    }

    private String buildMarathiWeddingSeasonOfferLine(Campaign campaign) {
        if (campaign.getDiscountType() == null || campaign.getDiscountType().name().equals("NONE")) {
            return appendCouponSuffix(campaign, "लग्नसराईसाठी ब्रायडल आणि गिफ्ट कलेक्शन", "MARATHI");
        }
        BigDecimal value = campaign.getDiscountValue() == null ? BigDecimal.ZERO : campaign.getDiscountValue();
        String line = switch (campaign.getDiscountType()) {
            case PERCENTAGE -> "लग्नसराईसाठी ब्रायडल आणि गिफ्ट कलेक्शनवर %s%% पर्यंत सूट".formatted(value.stripTrailingZeros().toPlainString());
            case FLAT -> "लग्नसराईसाठी ब्रायडल आणि गिफ्ट कलेक्शनवर ₹%s पर्यंत सूट".formatted(value.stripTrailingZeros().toPlainString());
            case NONE -> "लग्नसराईसाठी ब्रायडल आणि गिफ्ट कलेक्शन";
        };
        return appendCouponSuffix(campaign, line, "MARATHI");
    }

    private String marathiDiscountObject(String offerSubject, MarketingOccasionLibrary.Occasion festivalContext) {
        if (isOccasion(festivalContext, "mothers-day")) {
            return "आईसाठी खास भेटवस्तूं";
        }
        if (isOccasion(festivalContext, "wedding-season")) {
            return "ब्रायडल आणि गिफ्ट कलेक्शन";
        }
        String subject = defaultString(offerSubject, "निवडक कलेक्शन");
        if (subject.endsWith("भेटवस्तू")) {
            return subject + "ं";
        }
        return subject;
    }

    private String buildMockWhatsAppLine(Campaign campaign, String productOrCampaign, String offerLine, MarketingOccasionLibrary.Occasion festivalContext) {
        String language = resolveLanguage(campaign);
        int variant = captionVariant(campaign, MarketingPlatform.WHATSAPP, 3);
        return switch (language) {
            case "MARATHI" -> {
                String subject = defaultString(productOrCampaign, "निवडक कलेक्शन");
                String offer = defaultString(offerLine, "मर्यादित काळासाठी खास सूट");
                String occasion = festivalContext == null ? "आजच्या खास खरेदीसाठी" : festivalDisplayName(festivalContext) + " साठी";
                yield switch (variant) {
                    case 1 -> "\"तुमचा लुक खास, तुमची निवड खास.\" %s %s. आवडलेले डिझाइन्स आजच पाहा."
                            .formatted(occasion, offer);
                    case 2 -> "%s सुंदर ज्वेलरी आणि स्टाईलिश कलेक्शनची निवड तयार आहे. %s. भेट द्या आणि तुमची फेव्हरेट निवड करा."
                            .formatted(occasion, offer);
                    default -> "%s तुमची खास स्टाईल तयार ठेवा. %s. %s मधून आवडती निवड आजच करा."
                            .formatted(occasion, offer, subject);
                };
            }
            case "HINGLISH" -> {
                String offer = defaultString(offerLine, "Limited period offer.");
                String subject = defaultString(productOrCampaign, "fresh jewellery and beauty picks");
                yield switch (variant) {
                    case 1 -> "Aaj ka look thoda extra special banao. %s. %s explore karo."
                            .formatted(offer, subject);
                    case 2 -> "Festive style ready hai. %s. Apni favourite design aaj hi choose karo."
                            .formatted(offer);
                    default -> "New look, fresh confidence. %s. %s ready for you."
                            .formatted(offer, subject);
                };
            }
            default -> {
                String offer = defaultString(offerLine, "Limited period offer.");
                String subject = defaultString(productOrCampaign, "fresh jewellery and beauty picks");
                yield switch (variant) {
                    case 1 -> "A small detail can make the whole look memorable. %s. Explore %s today."
                            .formatted(offer, subject);
                    case 2 -> "Your next favorite style is waiting. %s. Visit us today."
                            .formatted(offer);
                    default -> "Fresh styles, easy gifting, and looks made to be noticed. %s. Shop %s now."
                            .formatted(offer, subject);
                };
            }
        };
    }

    private String buildCreativePreview(Campaign campaign, String shopName, String productName, MarketingPlatform platform) {
        String title = defaultString(trimToNull(campaign.getCampaignName()), "Marketing Draft");
        String subtitle = defaultString(trimToNull(productName), defaultString(trimToNull(campaign.getOfferTitle()), platform.name()));
        String offerBadge = buildCreativeOfferBadge(campaign);
        MarketingOccasionLibrary.Occasion festivalContext = detectFestivalContext(campaign);
        String caption = buildMockCaption(campaign, shopName, productName, platform, festivalContext);
        String captionLines = svgTextBlock(caption, 92, 860, 48, 56, 3, "#fff8ec", 42, "700");
        String contact = defaultString(trimToNull(campaign.getLinkUrl()), "https://kpskrishnai.com");
        String logoMarkup = buildSvgLogoMarkup(resolveLogoUrl());
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="1200" viewBox="0 0 1200 1200">
                  <defs>
                    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%%" stop-color="#1f1a17"/>
                      <stop offset="100%%" stop-color="#6d4b1d"/>
                    </linearGradient>
                    <radialGradient id="glow" cx="50%%" cy="30%%" r="60%%">
                      <stop offset="0%%" stop-color="#f6d59a" stop-opacity="0.95"/>
                      <stop offset="100%%" stop-color="#f6d59a" stop-opacity="0"/>
                    </radialGradient>
                  </defs>
                  <rect width="1200" height="1200" fill="url(#bg)"/>
                  <circle cx="900" cy="300" r="320" fill="url(#glow)"/>
                  <rect x="80" y="90" rx="34" ry="34" width="132" height="132" fill="#1b3b34" fill-opacity="0.78" stroke="#f6d59a" stroke-opacity="0.35"/>
                  %s
                  <text x="96" y="430" fill="#ffffff" font-family="Georgia, serif" font-size="110" font-weight="700">%s</text>
                  <text x="96" y="520" fill="#f4d8a6" font-family="Arial, Helvetica, sans-serif" font-size="46">%s</text>
                  <rect x="96" y="600" rx="22" ry="22" width="420" height="72" fill="#f6d59a" fill-opacity="0.16" stroke="#f6d59a" stroke-opacity="0.48"/>
                  <text x="130" y="646" fill="#fff3da" font-family="Arial, Helvetica, sans-serif" font-size="34" font-weight="700">%s</text>
                  <rect x="72" y="790" rx="38" ry="38" width="1056" height="250" fill="#211814" fill-opacity="0.74" stroke="#f6d59a" stroke-opacity="0.35"/>
                  %s
                  <text x="92" y="1114" fill="#f6d59a" font-family="Arial, Helvetica, sans-serif" font-size="30" letter-spacing="3">%s</text>
                </svg>
                """.formatted(logoMarkup, escapeSvg(title), escapeSvg(subtitle), escapeSvg(offerBadge), captionLines, escapeSvg(contact));
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private String buildSvgLogoMarkup(String logoUrl) {
        if (isBlank(logoUrl)) {
            return "";
        }
        return """
                  <rect x="104" y="112" rx="24" ry="24" width="92" height="92" fill="#fffaf0" fill-opacity="0.96" stroke="#f6d59a" stroke-opacity="0.52"/>
                  <image href="%s" x="116" y="124" width="68" height="68" preserveAspectRatio="xMidYMid meet"/>
                """.formatted(escapeSvgAttribute(logoUrl));
    }

    private String svgTextBlock(String value,
                                int x,
                                int y,
                                int maxChars,
                                int lineHeight,
                                int maxLines,
                                String color,
                                int fontSize,
                                String fontWeight) {
        List<String> lines = splitTextLines(value, maxChars, maxLines);
        StringBuilder builder = new StringBuilder();
        builder.append("<text x=\"").append(x).append("\" y=\"").append(y)
                .append("\" fill=\"").append(color)
                .append("\" font-family=\"Arial, Helvetica, sans-serif\" font-size=\"")
                .append(fontSize).append("\" font-weight=\"").append(fontWeight).append("\">");
        for (int index = 0; index < lines.size(); index++) {
            if (index == 0) {
                builder.append("<tspan x=\"").append(x).append("\" dy=\"0\">");
            } else {
                builder.append("<tspan x=\"").append(x).append("\" dy=\"").append(lineHeight).append("\">");
            }
            builder.append(escapeSvg(lines.get(index))).append("</tspan>");
        }
        builder.append("</text>");
        return builder.toString();
    }

    private List<String> splitTextLines(String value, int maxChars, int maxLines) {
        String cleaned = defaultString(value, "");
        String[] words = cleaned.split("\\s+");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (current.length() > 0 && current.length() + 1 + word.length() > maxChars) {
                lines.add(current.toString());
                current.setLength(0);
                if (lines.size() == maxLines) {
                    break;
                }
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0 && lines.size() < maxLines) {
            lines.add(current.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        if (lines.size() == maxLines && cleaned.length() > String.join(" ", lines).length()) {
            int lastIndex = lines.size() - 1;
            String last = lines.get(lastIndex);
            lines.set(lastIndex, last.length() > 1 ? last.substring(0, Math.max(1, last.length() - 1)) + "..." : last);
        }
        return lines;
    }

    private String escapeSvg(String value) {
        return safe(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String escapeSvgAttribute(String value) {
        return escapeSvg(value).replace("\"", "&quot;");
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String resolveLanguage(Campaign campaign) {
        return campaign.getLanguage() == null ? "MARATHI" : campaign.getLanguage().name();
    }

    private String campaignGoal(Campaign campaign) {
        String goal = safe(campaign.getCampaignGoal()).toUpperCase(Locale.ROOT);
        if (!goal.isBlank()) {
            return goal;
        }
        if (campaign.getCampaignType() != null) {
            return switch (campaign.getCampaignType()) {
                case OFFER -> "OFFER";
                case FESTIVAL, SEASONAL -> "GREETING";
                case NEW_ARRIVAL -> "PRODUCT_STORY";
                default -> "AWARENESS";
            };
        }
        return "AWARENESS";
    }

    private boolean isNoOfferCampaign(Campaign campaign) {
        boolean noDiscount = campaign.getDiscountType() == null || campaign.getDiscountType().name().equals("NONE");
        boolean noCoupon = isBlank(campaign.getCouponCode());
        String offerMode = safe(campaign.getOfferMode()).toUpperCase(Locale.ROOT);
        return noDiscount && noCoupon && (offerMode.isBlank() || offerMode.equals("NONE"));
    }

    private int captionVariant(Campaign campaign, MarketingPlatform platform, int count) {
        if (count <= 1) {
            return 0;
        }
        String seed = safe(campaign.getCampaignName()) + "|" + safe(campaign.getOfferTitle()) + "|"
                + safe(campaign.getOfferProduct()) + "|" + platform.name();
        return Math.floorMod(seed.hashCode(), count);
    }

    private String buildMarathiInstagramCaption(Campaign campaign,
                                                String productOrCampaign,
                                                String offerLine,
                                                MarketingOccasionLibrary.Occasion festivalContext) {
        int variant = captionVariant(campaign, MarketingPlatform.INSTAGRAM, 3);
        if (festivalContext != null) {
            String festival = festivalDisplayName(festivalContext);
            String offer = defaultString(offerLine, "निवडक कलेक्शनवर खास सूट");
            String subject = defaultString(productOrCampaign, "आमचे निवडक डिझाइन्स");
            return switch (variant) {
                case 1 -> "%s चा उत्सव, तुमच्या स्टाईलचा खास क्षण. %s. %s आजच एक्सप्लोर करा."
                        .formatted(festival, offer, subject);
                case 2 -> "\"सण येतो तेव्हा सजणंही खास असावं.\" %s साठी %s. %s मधून तुमची आवडती निवड करा."
                        .formatted(festival, offer, subject);
                default -> "\"आजचा लुक आठवणीत राहू द्या.\" %s साठी %s. %s कलेक्शन पाहा."
                        .formatted(festival, offer, subject);
            };
        }
        String subject = defaultString(productOrCampaign, "तुमच्यासाठी निवडक कलेक्शन");
        String offer = defaultString(offerLine, "निवडक कलेक्शनवर खास सूट");
        return switch (variant) {
            case 1 -> "एक सुंदर दागिना संपूर्ण लुक बदलू शकतो. %s मध्ये %s. तुमची आवडती निवड करा."
                    .formatted(subject, offer);
            case 2 -> "\"सौंदर्य छोट्या तपशीलांत असतं.\" %s साठी %s. कलेक्शन आजच पाहा."
                    .formatted(subject, offer);
            default -> "\"तुमची स्टाईल बोलू द्या.\" %s साठी %s. निवडक कलेक्शन आजच पाहा."
                    .formatted(subject, offer);
        };
    }

    private String buildMarathiFacebookCaption(Campaign campaign,
                                               String shopName,
                                               String productOrCampaign,
                                               String offerLine,
                                               MarketingOccasionLibrary.Occasion festivalContext) {
        int variant = captionVariant(campaign, MarketingPlatform.FACEBOOK, 3);
        String visitLine = defaultString(festivalContext == null ? null : festivalContext.marathiVisitCta(), "आजच दुकानात किंवा वेबसाईटवर भेट द्या");
        if (festivalContext != null) {
            String festival = festivalDisplayName(festivalContext);
            String offer = defaultString(offerLine, "निवडक कलेक्शनवर खास सूट");
            String subject = defaultString(productOrCampaign, "निवडक डिझाइन्स");
            return switch (variant) {
                case 1 -> "\"आनंद साजरा करायचा असेल, तर लुकही खास हवा.\" %s साठी %s. %s मधून घरातील प्रत्येक खास क्षणासाठी सुंदर निवड करा. %s."
                        .formatted(festival, offer, subject, visitLine);
                case 2 -> "%s च्या शुभ प्रसंगी तुमच्या स्टाईलला नवा स्पर्श द्या. %s. %s मधील आकर्षक डिझाइन्स पाहा आणि आवडती निवड आजच राखून ठेवा. %s."
                        .formatted(festival, offer, subject, visitLine);
                default -> "\"सण सुंदर आठवणींसाठी, आणि आठवणी सुंदर स्टाईलसाठी.\" %s साठी %s. %s मधून तुमचा उत्सवी लुक पूर्ण करा. %s."
                        .formatted(festival, offer, subject, visitLine);
            };
        }
        String subject = defaultString(productOrCampaign, "तुमच्या खास खरेदीसाठी");
        String offer = defaultString(offerLine, "निवडक कलेक्शनवर खास सूट");
        return switch (variant) {
            case 1 -> "\"दररोजच्या लुकला थोडी खास ओळख द्या.\" %s मध्ये %s. आवडलेले डिझाइन्स पाहण्यासाठी %s."
                    .formatted(subject, offer, visitLine);
            case 2 -> "तुमच्या स्टाईलची पुढची सुंदर गोष्ट इथून सुरू होते. %s मधील %s. %s."
                    .formatted(subject, offer, visitLine);
            default -> "\"छोटीशी निवड, मोठा कॉन्फिडन्स.\" %s साठी %s. %s."
                    .formatted(subject, offer, visitLine);
        };
    }

    private String buildEnglishInstagramCaption(Campaign campaign,
                                                String shopName,
                                                String productOrCampaign,
                                                String offerLine,
                                                MarketingOccasionLibrary.Occasion festivalContext) {
        int variant = captionVariant(campaign, MarketingPlatform.INSTAGRAM, 3);
        String occasion = festivalContext == null ? "today" : festivalContext.englishName();
        String subject = defaultString(productOrCampaign, "curated jewellery and beauty picks");
        String offer = defaultString(offerLine, "limited-time festive picks");
        return switch (variant) {
            case 1 -> "\"A small detail can change the whole look.\" %s is here with %s. Explore %s today."
                    .formatted(occasion, offer, subject);
            case 2 -> "Your festive look deserves a finishing touch. %s. Shop %s before the favorites sell out."
                    .formatted(offer, subject);
            default -> "\"Wear the moment, not just the outfit.\" %s brings %s for %s."
                    .formatted(occasion, subject, offer);
        };
    }

    private String buildEnglishFacebookCaption(Campaign campaign,
                                               String shopName,
                                               String productOrCampaign,
                                               String offerLine,
                                               MarketingOccasionLibrary.Occasion festivalContext) {
        int variant = captionVariant(campaign, MarketingPlatform.FACEBOOK, 3);
        String occasion = festivalContext == null ? "this season" : festivalContext.englishName();
        String subject = defaultString(productOrCampaign, "curated jewellery and beauty picks");
        String offer = defaultString(offerLine, "limited-time picks");
        return switch (variant) {
            case 1 -> "\"The right accessory makes every celebration feel personal.\" For %s, explore %s with %s. Visit the website today."
                    .formatted(occasion, subject, offer);
            case 2 -> "Some styles are made for compliments. Discover %s and make %s feel extra special with %s."
                    .formatted(subject, occasion, offer);
            default -> "\"Your best look should be easy to choose.\" Fresh %s for %s. %s. Visit now."
                    .formatted(subject, occasion, offer);
        };
    }

    private String resolveShowcaseSubject(Campaign campaign, String productName, MarketingOccasionLibrary.Occasion festivalContext) {
        String cleanedProductName = normalizeOfferTitle(productName, festivalContext);
        if (!isBlank(cleanedProductName)) {
            return cleanedProductName;
        }
        String cleanedOfferProduct = normalizeOfferTitle(campaign.getOfferProduct(), festivalContext);
        if (!isBlank(cleanedOfferProduct)) {
            return cleanedOfferProduct;
        }
        return resolveLanguage(campaign).equals("MARATHI") ? "आमचे निवडक डिझाइन्स" : "our curated picks";
    }

    private String resolveOfferSubject(Campaign campaign, MarketingOccasionLibrary.Occasion festivalContext, String language) {
        if ("MARATHI".equals(language) && isOccasion(festivalContext, "mothers-day")) {
            return "आईसाठी खास भेटवस्तू";
        }
        if ("MARATHI".equals(language) && isOccasion(festivalContext, "wedding-season")) {
            return "ब्रायडल आणि गिफ्ट कलेक्शन";
        }
        String cleanedOfferProduct = normalizeOfferTitle(campaign.getOfferProduct(), festivalContext);
        if (!isBlank(cleanedOfferProduct)) {
            return cleanedOfferProduct;
        }
        String cleanedOfferTitle = normalizeOfferTitle(campaign.getOfferTitle(), festivalContext);
        if (!isBlank(cleanedOfferTitle)) {
            return cleanedOfferTitle;
        }
        return switch (language) {
            case "MARATHI" -> "निवडक कलेक्शन";
            case "HINGLISH" -> "selected collection";
            default -> "selected collection";
        };
    }

    private String normalizeOfferTitle(String value, MarketingOccasionLibrary.Occasion festivalContext) {
        String cleaned = safe(value)
                .replaceAll("(?i)\\boffer\\b", " ")
                .replaceAll("(?i)\\bsale\\b", " ")
                .replace("ऑफर", " ")
                .replace("सेल", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) {
            return null;
        }
        if (festivalContext != null) {
            String comparable = comparableText(cleaned);
            String festivalMarathi = comparableText(festivalContext.marathiName());
            String festivalEnglish = comparableText(festivalContext.englishName());
            if (comparable.equals(festivalMarathi) || comparable.equals(festivalEnglish)) {
                return null;
            }
        }
        return cleaned;
    }

    private String comparableText(String value) {
        return safe(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}]+", "");
    }

    private String polishGeneratedCaption(String caption,
                                          Campaign campaign,
                                          String productName,
                                          MarketingPlatform platform,
                                          MarketingOccasionLibrary.Occasion festivalContext) {
        String cleaned = defaultString(caption, buildMockCaption(campaign, "Retail Shop", productName, platform, festivalContext));
        if (festivalContext != null) {
            String festival = festivalContext.marathiName();
            cleaned = cleaned.replace(festival + " निमित्त " + festival, festival + " निमित्त खास ऑफर");
            cleaned = cleaned.replace(festival + " निमित्त " + festival + ".", festival + " निमित्त खास ऑफर.");
            cleaned = cleaned.replace(festival + " Offer", "निवडक कलेक्शन");
            cleaned = cleaned.replace(festival + " ऑफर", "खास ऑफर");
        }
        cleaned = cleaned
                .replaceAll("\\s{2,}", " ")
                .replaceAll("\\.\\.", ".")
                .trim();
        if (isWeakCaption(cleaned, campaign, festivalContext)) {
            return buildMockCaption(campaign, "Retail Shop", productName, platform, festivalContext);
        }
        return normalizeMarathiMarketingText(cleaned);
    }

    private String ensureMarathiCaptionQuality(String caption,
                                               Campaign campaign,
                                               String shopName,
                                               String productName,
                                               MarketingPlatform platform,
                                               MarketingOccasionLibrary.Occasion festivalContext) {
        if (!"MARATHI".equals(resolveLanguage(campaign))) {
            return caption;
        }
        if (isOccasion(festivalContext, "mothers-day")) {
            return normalizeMarathiMarketingText(buildMarathiMothersDayCaption(campaign, shopName, platform));
        }
        if (isOccasion(festivalContext, "wedding-season")) {
            return normalizeMarathiMarketingText(buildMarathiWeddingSeasonCaption(campaign, shopName, platform));
        }
        String cleaned = normalizeMarathiMarketingText(caption);
        if (isWeakCaption(cleaned, campaign, festivalContext)) {
            return normalizeMarathiMarketingText(buildMockCaption(campaign, shopName, productName, platform, festivalContext));
        }
        return cleaned;
    }

    private String ensureMarathiHeadlineQuality(String headline,
                                                Campaign campaign,
                                                MarketingOccasionLibrary.Occasion festivalContext) {
        if (!"MARATHI".equals(resolveLanguage(campaign))) {
            return headline;
        }
        String cleaned = normalizeMarathiOverlayText(headline, campaign, festivalContext);
        if (isOccasion(festivalContext, "mothers-day")) {
            return buildMarathiMothersDayOfferLine(campaign);
        }
        if (isOccasion(festivalContext, "wedding-season")) {
            return buildMarathiWeddingSeasonOfferLine(campaign);
        }
        if (hasBadMarathiCopy(cleaned) || hasAwkwardMarathiOverlayText(cleaned, festivalContext)) {
            return buildSafeMarathiOverlayFallback(campaign, festivalContext);
        }
        return cleaned;
    }

    private String normalizeMarathiOverlayText(String value,
                                               Campaign campaign,
                                               MarketingOccasionLibrary.Occasion festivalContext) {
        String cleaned = normalizeMarathiMarketingText(value);
        if (festivalContext == null) {
            return cleaned;
        }
        String festival = festivalDisplayName(festivalContext);
        String possessive = marathiOccasionPossessive(festivalContext);
        String dative = marathiOccasionDative(festivalContext);
        cleaned = cleaned
                .replace(festival + " साठी " + festival + "च्या शुभेच्छा", possessive + " हार्दिक शुभेच्छा")
                .replace(festival + " साठी " + festival + "च्या हार्दिक शुभेच्छा", possessive + " हार्दिक शुभेच्छा")
                .replace(festival + " साठी " + festival, festival)
                .replace(festival + " साठी", dative)
                .replace(festival + " निमित्त " + festival + "च्या शुभेच्छा", possessive + " हार्दिक शुभेच्छा")
                .replace(festival + " निमित्त " + festival, festival + " निमित्त");
        if (isNoOfferCampaign(campaign) && hasRepeatedOccasion(cleaned, festivalContext)) {
            return buildNoOfferMarathiImageHeadline(campaign, null, festivalContext);
        }
        return normalizeMarathiMarketingText(cleaned);
    }

    private String buildSafeMarathiOverlayFallback(Campaign campaign,
                                                   MarketingOccasionLibrary.Occasion festivalContext) {
        if (isNoOfferCampaign(campaign)) {
            return normalizeMarathiMarketingText(buildNoOfferMarathiImageHeadline(campaign, null, festivalContext));
        }
        String offerLine = buildOfferLine(campaign, festivalContext);
        if (festivalContext != null && !mentionsOccasion(offerLine, festivalContext)) {
            return normalizeMarathiMarketingText(marathiOccasionDative(festivalContext) + " " + offerLine);
        }
        return normalizeMarathiMarketingText(defaultString(offerLine, "निवडक कलेक्शन पाहा"));
    }

    private String normalizeMarathiMarketingText(String value) {
        return safe(value)
                .replace("मातु दिन", "मातृ दिन")
                .replace("मातु", "मातृ")
                .replace("मातृ दिन साठी", "मातृ दिनासाठी")
                .replace("मातृदिन साठी", "मातृदिनासाठी")
                .replace("मातृ दिनासाठी आईंसाठी", "आईसाठी")
                .replace("मातृ दिनासाठी आईसाठी", "आईसाठी")
                .replace("आईंसाठी", "आईसाठी")
                .replace("जपरी", "काळजीपूर्वक")
                .replace("तिची देखभाल करा", "तिच्यासाठी सुंदर भेट निवडा")
                .replace("तुमची देखभाल करा", "तिच्यासाठी सुंदर भेट निवडा")
                .replace("लकशरी", "लक्झरी")
                .replace("लग्नाचा सीझन साठी", "लग्नसराईसाठी")
                .replace("लग्नाच्या सीझनसाठी", "लग्नसराईसाठी")
                .replace("हा संदेश शेअर करा", "आता भेट द्या")
                .replace("Share this message", "Visit now")
                .replace("खास खास", "खास")
                .replaceAll("साठी\\s+साठी", "साठी")
                .replaceAll("वर\\s+वर", "वर")
                .replaceAll("\\s+([।.!?,])", "$1")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private boolean isWeakCaption(String caption,
                                  Campaign campaign,
                                  MarketingOccasionLibrary.Occasion festivalContext) {
        String cleaned = safe(caption);
        if (cleaned.length() < 42) {
            return true;
        }
        if (cleaned.contains("Offer वर") || cleaned.contains("खास ऑफर उपलब्ध")) {
            return true;
        }
        if (hasBadMarathiCopy(cleaned)) {
            return true;
        }
        String comparableCaption = comparableText(cleaned);
        String campaignName = comparableText(campaign.getCampaignName());
        String offerTitle = comparableText(campaign.getOfferTitle());
        if (!campaignName.isBlank() && comparableCaption.equals(campaignName)) {
            return true;
        }
        if (!offerTitle.isBlank() && comparableCaption.equals(offerTitle)) {
            return true;
        }
        if (festivalContext != null) {
            String festival = comparableText(festivalContext.marathiName());
            return !festival.isBlank() && comparableCaption.indexOf(festival) != comparableCaption.lastIndexOf(festival);
        }
        return false;
    }

    private boolean hasBadMarathiCopy(String value) {
        String cleaned = safe(value);
        return cleaned.contains("मातु")
                || cleaned.contains("जपरी")
                || cleaned.contains("तिची देखभाल करा")
                || cleaned.contains("तुमची देखभाल करा")
                || cleaned.contains("मातृ दिनासाठी आईंसाठी")
                || cleaned.contains("मातृ दिनासाठी आईसाठी")
                || cleaned.contains("मातृ दिन साठी")
                || cleaned.contains("आईंसाठी")
                || cleaned.contains("लकशरी")
                || cleaned.contains("लग्नाचा सीझन साठी")
                || cleaned.contains("लग्नाच्या सीझनसाठी")
                || cleaned.contains("दिवाळी साठी")
                || cleaned.contains(" साठी दिवाळीच्या शुभेच्छा")
                || cleaned.contains("साठी साठी")
                || cleaned.contains("वर वर");
    }

    private String polishGeneratedCta(String cta,
                                      Campaign campaign,
                                      MarketingPlatform platform,
                                      MarketingOccasionLibrary.Occasion festivalContext) {
        if ("MARATHI".equals(resolveLanguage(campaign)) && isOccasion(festivalContext, "mothers-day")) {
            return platform == MarketingPlatform.WHATSAPP ? "आईसाठी भेट निवडा" : "आईसाठी कलेक्शन पाहा";
        }
        if ("MARATHI".equals(resolveLanguage(campaign)) && isOccasion(festivalContext, "wedding-season")) {
            return "लग्नसराई कलेक्शन पाहा";
        }
        if (festivalContext == null) {
            return "MARATHI".equals(resolveLanguage(campaign)) ? normalizeMarathiMarketingText(cta) : cta;
        }
        String polished = cta.replace(festivalContext.marathiName() + " निमित्त " + festivalContext.marathiName(),
                festivalContext.marathiName() + " निमित्त खास ऑफर");
        return "MARATHI".equals(resolveLanguage(campaign)) ? normalizeMarathiMarketingText(polished) : polished;
    }

    private String buildCreativeOfferBadge(Campaign campaign) {
        MarketingOccasionLibrary.Occasion festivalContext = detectFestivalContext(campaign);
        if (isNoOfferCampaign(campaign) && !"OFFER".equals(campaignGoal(campaign))) {
            return null;
        }
        if (campaign.getDiscountType() == null || campaign.getDiscountType().name().equals("NONE")) {
            return defaultString(normalizeOfferTitle(campaign.getOfferTitle(), festivalContext), "Premium picks");
        }
        BigDecimal value = campaign.getDiscountValue() == null ? BigDecimal.ZERO : campaign.getDiscountValue();
        return switch (campaign.getDiscountType()) {
            case PERCENTAGE -> value.stripTrailingZeros().toPlainString() + "% पर्यंत सूट";
            case FLAT -> "₹" + value.stripTrailingZeros().toPlainString() + " पर्यंत सूट";
            case NONE -> defaultString(normalizeOfferTitle(campaign.getOfferTitle(), festivalContext), "Premium picks");
        };
    }

    private String buildEnglishOfferBadge(Campaign campaign) {
        MarketingOccasionLibrary.Occasion festivalContext = detectFestivalContext(campaign);
        if (campaign.getDiscountType() == null || campaign.getDiscountType().name().equals("NONE")) {
            return defaultString(normalizeOfferTitle(campaign.getOfferTitle(), festivalContext),
                    defaultString(normalizeOfferTitle(campaign.getCampaignName(), festivalContext), "Premium picks"));
        }
        BigDecimal value = campaign.getDiscountValue() == null ? BigDecimal.ZERO : campaign.getDiscountValue();
        return switch (campaign.getDiscountType()) {
            case PERCENTAGE -> value.stripTrailingZeros().toPlainString() + "% off";
            case FLAT -> "₹" + value.stripTrailingZeros().toPlainString() + " off";
            case NONE -> defaultString(normalizeOfferTitle(campaign.getOfferTitle(), festivalContext), "Premium picks");
        };
    }

    private String describeFestivalContext(MarketingOccasionLibrary.Occasion festivalContext) {
        if (festivalContext == null) {
            return "No exact festival inferred. Use the title and tone to create a locally relevant Marathi retail message.";
        }
        return "%s / %s with cues like %s"
                .formatted(festivalContext.marathiName(), festivalContext.englishName(), festivalContext.visualHints());
    }

    private String festivalDisplayName(MarketingOccasionLibrary.Occasion festivalContext) {
        return festivalContext == null ? null : festivalContext.marathiName();
    }

    private String marathiOccasionPossessive(MarketingOccasionLibrary.Occasion festivalContext) {
        if (festivalContext == null) {
            return "या खास प्रसंगाच्या";
        }
        return switch (festivalContext.key()) {
            case "diwali" -> "दिवाळीच्या";
            case "gudi-padwa" -> "गुढी पाडव्याच्या";
            case "akshaya-tritiya" -> "अक्षय तृतीयेच्या";
            case "makar-sankranti" -> "मकर संक्रांतीच्या";
            case "mothers-day" -> "मातृ दिनाच्या";
            case "wedding-season" -> "लग्नसराईच्या";
            case "ganesh-chaturthi" -> "गणेश चतुर्थीच्या";
            case "navratri" -> "नवरात्रीच्या";
            case "dussehra" -> "दसऱ्याच्या";
            case "christmas" -> "ख्रिसमसच्या";
            case "womens-day" -> "महिला दिनाच्या";
            case "maharashtra-day" -> "महाराष्ट्र दिनाच्या";
            case "raksha-bandhan" -> "रक्षाबंधनाच्या";
            case "holi" -> "होळीच्या";
            default -> festivalContext.marathiName() + " निमित्त";
        };
    }

    private String marathiOccasionDative(MarketingOccasionLibrary.Occasion festivalContext) {
        if (festivalContext == null) {
            return "या खास प्रसंगासाठी";
        }
        return switch (festivalContext.key()) {
            case "diwali" -> "दिवाळीसाठी";
            case "gudi-padwa" -> "गुढी पाडव्यासाठी";
            case "akshaya-tritiya" -> "अक्षय तृतीयेसाठी";
            case "makar-sankranti" -> "मकर संक्रांतीसाठी";
            case "mothers-day" -> "मातृ दिनासाठी";
            case "wedding-season" -> "लग्नसराईसाठी";
            case "ganesh-chaturthi" -> "गणेश चतुर्थीसाठी";
            case "navratri" -> "नवरात्रीसाठी";
            case "dussehra" -> "दसऱ्यासाठी";
            case "christmas" -> "ख्रिसमससाठी";
            case "womens-day" -> "महिला दिनासाठी";
            case "maharashtra-day" -> "महाराष्ट्र दिनासाठी";
            case "raksha-bandhan" -> "रक्षाबंधनासाठी";
            case "holi" -> "होळीसाठी";
            default -> festivalContext.marathiName() + "साठी";
        };
    }

    private boolean hasAwkwardMarathiOverlayText(String value,
                                                 MarketingOccasionLibrary.Occasion festivalContext) {
        String cleaned = safe(value);
        if (festivalContext != null && hasRepeatedOccasion(cleaned, festivalContext)) {
            return true;
        }
        return cleaned.contains(" साठी ")
                && (cleaned.contains("च्या शुभेच्छा") || cleaned.contains("हार्दिक शुभेच्छा"));
    }

    private boolean hasRepeatedOccasion(String value, MarketingOccasionLibrary.Occasion festivalContext) {
        if (festivalContext == null) {
            return false;
        }
        String comparable = comparableText(value);
        String festival = comparableText(festivalContext.marathiName());
        return !festival.isBlank() && countOccurrences(comparable, festival) > 1;
    }

    private boolean mentionsOccasion(String value, MarketingOccasionLibrary.Occasion festivalContext) {
        if (festivalContext == null || value == null) {
            return false;
        }
        String comparable = comparableText(value);
        String marathi = comparableText(festivalContext.marathiName());
        String english = comparableText(festivalContext.englishName());
        return !marathi.isBlank() && comparable.contains(marathi)
                || !english.isBlank() && comparable.contains(english);
    }

    private int countOccurrences(String value, String token) {
        if (value == null || token == null || token.isBlank()) {
            return 0;
        }
        int count = 0;
        int index = value.indexOf(token);
        while (index >= 0) {
            count++;
            index = value.indexOf(token, index + token.length());
        }
        return count;
    }

    private boolean isOccasion(MarketingOccasionLibrary.Occasion festivalContext, String key) {
        return festivalContext != null && festivalContext.key().equals(key);
    }

    private MarketingOccasionLibrary.Occasion detectFestivalContext(Campaign campaign) {
        return MarketingOccasionLibrary.detectOccasion(
                defaultString(campaign.getCampaignName(), ""),
                defaultString(campaign.getOfferTitle(), ""),
                defaultString(campaign.getOfferProduct(), "")
        );
    }

    private record GeneratedImagePayload(byte[] imageBytes, String contentType, String sourceUrl) {
    }
}
