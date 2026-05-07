package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.MarketingProperties;
import com.retailshop.dto.ImageUploadResponse;
import com.retailshop.entity.Campaign;
import com.retailshop.enums.MarketingPlatform;
import com.retailshop.service.AIContentGenerationService;
import com.retailshop.service.ImageUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class AIContentGenerationServiceImpl implements AIContentGenerationService {

    private final MarketingProperties marketingProperties;
    private final ObjectMapper objectMapper;
    private final ImageUploadService imageUploadService;
    private final HttpClient httpClient;

    @Autowired
    public AIContentGenerationServiceImpl(MarketingProperties marketingProperties,
                                          ObjectMapper objectMapper,
                                          ImageUploadService imageUploadService) {
        this(marketingProperties, objectMapper, imageUploadService, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build());
    }

    AIContentGenerationServiceImpl(MarketingProperties marketingProperties,
                                   ObjectMapper objectMapper,
                                   ImageUploadService imageUploadService,
                                   HttpClient httpClient) {
        this.marketingProperties = marketingProperties;
        this.objectMapper = objectMapper;
        this.imageUploadService = imageUploadService;
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
                Understand Indian festival and occasion names mentioned in the campaign name or offer title and reflect that context correctly in the wording, emotion, gifting angle, and visual mood.
                Never repeat the festival name back-to-back or create awkward patterns like "{festival} निमित्त {festival}".
                If the offer title already contains the festival name, avoid echoing the same name again in the first line.
                Write thumb-stopping captions with a strong first-line hook, a quote-like emotional line when useful, and a clear shopping pull.
                Keep Instagram captions short, elegant, and scroll-stopping, with a memorable first sentence.
                Keep Facebook captions slightly longer, warm, persuasive, and ready for a real campaign.
                Avoid dull phrasing like "खास ऑफर उपलब्ध", "आजच खरेदी करा" as the whole caption, or plain title repetition.
                Do not use static-sounding templates; make every caption feel like polished campaign copy.
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
        String resolvedHashtags = defaultString(hashtags, buildMockHashtags(campaign, categoryName, platform, festivalContext));
        String resolvedCta = polishGeneratedCta(defaultString(cta, buildMockCta(campaign, platform, festivalContext)), festivalContext);
        String resolvedImagePrompt = defaultString(imagePrompt, buildImagePrompt(campaign, shopName, categoryName, productName, platform, festivalContext));
        String resolvedImageUrl = generateCreativeImage(campaign, shopName, productName, platform, resolvedImagePrompt);

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
        String imagePrompt = buildImagePrompt(campaign, shopName, categoryName, productName, platform, festivalContext);
        return new GeneratedMarketingDraft(
                buildMockCaption(campaign, shopName, productName, platform, festivalContext),
                buildMockHashtags(campaign, categoryName, platform, festivalContext),
                buildMockCta(campaign, platform, festivalContext),
                imagePrompt,
                generateCreativeImage(campaign, shopName, productName, platform, imagePrompt)
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
            GeneratedImagePayload imagePayload = switch (defaultString(marketingProperties.getAi().getImageProvider(), "OPENAI").toUpperCase(Locale.ROOT)) {
                case "OPENAI" -> generateOpenAiImagePayload(imagePrompt);
                case "LEONARDO" -> generateLeonardoImagePayload(imagePrompt);
                default -> null;
            };
            if (imagePayload == null) {
                return fallbackPreview;
            }
            if (imagePayload.imageBytes() != null && imagePayload.imageBytes().length > 0) {
                ImageUploadResponse uploadResponse = imageUploadService.uploadImageBytes(imagePayload.imageBytes(), imagePayload.contentType(), "marketing-campaigns");
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

    private GeneratedImagePayload generateLeonardoImagePayload(String imagePrompt) throws IOException, InterruptedException {
        MarketingProperties.Leonardo leonardo = marketingProperties.getLeonardo();
        if (leonardo == null || isBlank(leonardo.getApiKey())) {
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modelId", defaultString(leonardo.getModelId(), "de7d3faf-762f-48e0-b3b7-9d0ac3a3fcf3"));
        payload.put("contrast", leonardo.getContrast());
        payload.put("prompt", imagePrompt);
        payload.put("num_images", 1);
        payload.put("width", leonardo.getWidth());
        payload.put("height", leonardo.getHeight());
        payload.put("alchemy", leonardo.isAlchemy());
        payload.put("styleUUID", defaultString(leonardo.getStyleUuid(), "111dc692-d470-4eec-b791-3475abac4c46"));
        payload.put("enhancePrompt", leonardo.isEnhancePrompt());
        payload.put("public", leonardo.isPublicImages());

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://cloud.leonardo.ai/api/rest/v1/generations"))
                .header("accept", "application/json")
                .header("authorization", "Bearer " + leonardo.getApiKey().trim())
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Leonardo image generation failed with status " + response.statusCode() + ": " + extractApiErrorMessage(response.body()));
        }

        JsonNode root = objectMapper.readTree(response.body());
        String generationId = trimToNull(root.path("sdGenerationJob").path("generationId").asText(""));
        if (generationId == null) {
            throw new IOException("Leonardo image generation returned no generation ID");
        }

        return pollLeonardoImage(leonardo.getApiKey().trim(), generationId, Math.max(leonardo.getPollAttempts(), 1), Math.max(leonardo.getPollDelayMs(), 250));
    }

    private GeneratedImagePayload pollLeonardoImage(String apiKey,
                                                    String generationId,
                                                    int attempts,
                                                    long delayMs) throws IOException, InterruptedException {
        String lastStatus = "PENDING";
        for (int attempt = 0; attempt < attempts; attempt++) {
            HttpRequest statusRequest = HttpRequest.newBuilder(URI.create("https://cloud.leonardo.ai/api/rest/v1/generations/" + generationId))
                    .header("accept", "application/json")
                    .header("authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> statusResponse = httpClient.send(statusRequest, HttpResponse.BodyHandlers.ofString());
            if (statusResponse.statusCode() >= 400) {
                throw new IOException("Leonardo generation status check failed with status " + statusResponse.statusCode() + ": " + extractApiErrorMessage(statusResponse.body()));
            }

            JsonNode generationNode = objectMapper.readTree(statusResponse.body()).path("generations_by_pk");
            lastStatus = defaultString(trimToNull(generationNode.path("status").asText("")), lastStatus);
            if ("COMPLETE".equalsIgnoreCase(lastStatus)) {
                JsonNode imageNode = generationNode.path("generated_images").path(0);
                String imageUrl = trimToNull(imageNode.path("url").asText(""));
                if (imageUrl == null) {
                    throw new IOException("Leonardo completed without an image URL");
                }
                return downloadRemoteImageWithFallback(imageUrl, 5, 1500);
            }
            if ("FAILED".equalsIgnoreCase(lastStatus)) {
                throw new IOException("Leonardo generation failed for " + generationId);
            }
            Thread.sleep(delayMs);
        }
        throw new IOException("Leonardo generation did not complete in time. Last status: " + lastStatus);
    }

    private GeneratedImagePayload downloadRemoteImageWithFallback(String imageUrl,
                                                                  int maxAttempts,
                                                                  long delayMs) throws IOException, InterruptedException {
        IOException lastException = null;
        for (int attempt = 0; attempt < Math.max(maxAttempts, 1); attempt++) {
            try {
                HttpResponse<byte[]> imageResponse = httpClient.send(
                        HttpRequest.newBuilder(URI.create(imageUrl)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                );
                if (imageResponse.statusCode() >= 400 || imageResponse.body() == null || imageResponse.body().length == 0) {
                    throw new IOException("Image download failed with status " + imageResponse.statusCode());
                }
                String contentType = defaultString(imageResponse.headers().firstValue("content-type").orElse("image/png"), "image/png");
                contentType = contentType.split(";", 2)[0].trim();
                if (!contentType.startsWith("image/")) {
                    contentType = "image/png";
                }
                return new GeneratedImagePayload(imageResponse.body(), contentType, imageUrl);
            } catch (IOException exception) {
                lastException = exception;
                if (attempt < maxAttempts - 1) {
                    Thread.sleep(Math.max(delayMs, 250));
                }
            }
        }
        log.warn("Using Leonardo source URL directly because the generated image could not be downloaded yet: {}", imageUrl, lastException);
        return new GeneratedImagePayload(null, null, imageUrl);
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
                Category: %s
                Product: %s
                Offer title: %s
                Discount type: %s
                Discount value: %s
                Language: %s
                Tone: %s
                Start date: %s
                End date: %s
                Festival context inferred from the title: %s

                Generate:
                - Instagram: short premium caption, 8-15 hashtags, luxury CTA
                - Facebook: slightly longer emotional/festival caption
                - WhatsApp: short direct message for the template body line only, no full message body and no hashtag overload
                - If language is MARATHI, keep caption, CTA, and selling line fully in Marathi
                - If a festival is inferred, mention it naturally and accurately instead of generic festive wording
                - Use local Maharashtra-friendly retail phrasing for Marathi campaigns
                - Caption/message must start with an eye-catching hook or quote-style sentence, then the offer, then a simple action line
                - Avoid repeating campaign name or offer title as-is; turn it into attractive ad copy
                - For Marathi, use phrases like ज्वेलरी, नेकलेस, बांगड्या, इयररिंग्स, गिफ्ट, सौंदर्य, कलेक्शन when relevant
                - Avoid precious-metal-shop wording
                - Image prompt must be suitable for OpenAI image generation and include the exact shop name, offer badge, and short caption/message as readable text when applicable
                - If language is MARATHI, ask for clean readable Devanagari text and avoid English filler text
                - Do not invent fake logos, seals, brand marks, watermarks, placeholder text, or extra unreadable label blocks. Use shop name as plain text when no real logo asset is supplied.
                """.formatted(
                safe(campaign.getCampaignName()),
                safe(shopName),
                platform.name(),
                safe(campaign.getCampaignType() != null ? campaign.getCampaignType().name() : ""),
                safe(categoryName),
                safe(productName),
                safe(campaign.getOfferTitle()),
                safe(campaign.getDiscountType() != null ? campaign.getDiscountType().name() : ""),
                safe(campaign.getDiscountValue() != null ? campaign.getDiscountValue().toPlainString() : ""),
                safe(campaign.getLanguage() != null ? campaign.getLanguage().name() : ""),
                safe(campaign.getTone() != null ? campaign.getTone().name() : ""),
                safe(campaign.getStartDate() != null ? campaign.getStartDate().toString() : ""),
                safe(campaign.getEndDate() != null ? campaign.getEndDate().toString() : ""),
                describeFestivalContext(festivalContext)
        );
    }

    private String buildMockCaption(Campaign campaign,
                                    String shopName,
                                    String productName,
                                    MarketingPlatform platform,
                                    MarketingOccasionLibrary.Occasion festivalContext) {
        String productOrCampaign = resolveShowcaseSubject(campaign, productName, festivalContext);
        String offerLine = buildOfferLine(campaign, festivalContext);
        String language = resolveLanguage(campaign);
        if ("MARATHI".equals(language)) {
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

    private String buildMockHashtags(Campaign campaign, String categoryName, MarketingPlatform platform, MarketingOccasionLibrary.Occasion festivalContext) {
        if (platform == MarketingPlatform.WHATSAPP) {
            return "";
        }
        String normalizedCategory = defaultString(categoryName, "RetailStyle").replaceAll("[^A-Za-z0-9]", "");
        String language = resolveLanguage(campaign);
        if ("MARATHI".equals(language)) {
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
        if ("MARATHI".equals(language)) {
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

    private String buildImagePrompt(Campaign campaign,
                                    String shopName,
                                    String categoryName,
                                    String productName,
                                    MarketingPlatform platform,
                                    MarketingOccasionLibrary.Occasion festivalContext) {
        String festivalPrompt = festivalContext == null
                ? "general festive retail mood"
                : "%s festival mood with %s"
                .formatted(festivalContext.englishName(), festivalContext.visualHints());
        String offerBadge = buildCreativeOfferBadge(campaign);
        String landingUrl = defaultString(trimToNull(campaign.getLinkUrl()), "https://kpskrishnai.com");
        String captionText = buildMockCaption(campaign, shopName, productName, platform, festivalContext);
        return "Premium promotional creative for %s, %s campaign, focused on %s, in a %s tone, Indian women-focused jewellery and cosmetics retail aesthetic, pearl, ivory, emerald and deep maroon accent palette, elegant composition with necklaces, bangles, earrings, or beauty accessories where relevant, product-focused hero visual, %s, platform %s. Include only these exact text elements: shop name \"%s\", offer badge \"%s\", caption/message \"%s\", landing URL \"%s\". Render Marathi text in clean readable Devanagari. Do not use wording that implies a precious-metal shop. Do not invent any logo, emblem, fake phone number, fake website, watermark, placeholder phrase, English filler text, or unrelated text."
                .formatted(
                        defaultString(shopName, "retail brand"),
                        defaultString(campaign.getCampaignType() != null ? campaign.getCampaignType().name().replace('_', ' ').toLowerCase(Locale.ROOT) : null, "seasonal"),
                        defaultString(productName, defaultString(categoryName, "retail products")),
                        defaultString(campaign.getTone() != null ? campaign.getTone().name().toLowerCase(Locale.ROOT) : null, "premium"),
                        festivalPrompt,
                        platform.name().toLowerCase(Locale.ROOT),
                        defaultString(shopName, "retail brand"),
                        offerBadge,
                        captionText,
                        landingUrl
                );
    }

    private String buildOfferLine(Campaign campaign, MarketingOccasionLibrary.Occasion festivalContext) {
        String language = resolveLanguage(campaign);
        if (campaign.getDiscountType() == null || campaign.getDiscountType().name().equals("NONE")) {
            String cleanedOfferTitle = normalizeOfferTitle(campaign.getOfferTitle(), festivalContext);
            return defaultString(cleanedOfferTitle, "MARATHI".equals(language) ? defaultString(festivalDisplayName(festivalContext), "खास निवड") : defaultString(festivalContext == null ? null : festivalContext.englishName(), "Special picks"));
        }
        String offerSubject = resolveOfferSubject(campaign, festivalContext, language);
        BigDecimal value = campaign.getDiscountValue() == null ? BigDecimal.ZERO : campaign.getDiscountValue();
        if ("MARATHI".equals(language)) {
            return switch (campaign.getDiscountType()) {
                case PERCENTAGE -> "%s वर %s%% पर्यंत सूट".formatted(offerSubject, value.stripTrailingZeros().toPlainString());
                case FLAT -> "%s वर ₹%s पर्यंत सूट".formatted(offerSubject, value.stripTrailingZeros().toPlainString());
                case NONE -> offerSubject;
            };
        }
        if ("HINGLISH".equals(language)) {
            return switch (campaign.getDiscountType()) {
                case PERCENTAGE -> "%s par %s%% tak off".formatted(offerSubject, value.stripTrailingZeros().toPlainString());
                case FLAT -> "%s par flat %s off".formatted(offerSubject, value.stripTrailingZeros().toPlainString());
                case NONE -> offerSubject;
            };
        }
        return switch (campaign.getDiscountType()) {
            case PERCENTAGE -> "%s - save %s%%".formatted(offerSubject, value.stripTrailingZeros().toPlainString());
            case FLAT -> "%s - flat %s off".formatted(offerSubject, value.stripTrailingZeros().toPlainString());
            case NONE -> offerSubject;
        };
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
        String badge = defaultString(trimToNull(shopName), "Retail");
        String offerBadge = buildCreativeOfferBadge(campaign);
        MarketingOccasionLibrary.Occasion festivalContext = detectFestivalContext(campaign);
        String caption = buildMockCaption(campaign, shopName, productName, platform, festivalContext);
        String captionLines = svgTextBlock(caption, 92, 860, 48, 56, 3, "#fff8ec", 42, "700");
        String shopLines = svgTextBlock(badge, 96, 168, 42, 46, 2, "#fff6df", 38, "700");
        String contact = defaultString(trimToNull(campaign.getLinkUrl()), "https://kpskrishnai.com");
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
                  <rect x="80" y="90" rx="34" ry="34" width="470" height="132" fill="#1b3b34" fill-opacity="0.78" stroke="#f6d59a" stroke-opacity="0.35"/>
                  %s
                  <text x="96" y="430" fill="#ffffff" font-family="Georgia, serif" font-size="110" font-weight="700">%s</text>
                  <text x="96" y="520" fill="#f4d8a6" font-family="Arial, Helvetica, sans-serif" font-size="46">%s</text>
                  <rect x="96" y="600" rx="22" ry="22" width="420" height="72" fill="#f6d59a" fill-opacity="0.16" stroke="#f6d59a" stroke-opacity="0.48"/>
                  <text x="130" y="646" fill="#fff3da" font-family="Arial, Helvetica, sans-serif" font-size="34" font-weight="700">%s</text>
                  <rect x="72" y="790" rx="38" ry="38" width="1056" height="250" fill="#211814" fill-opacity="0.74" stroke="#f6d59a" stroke-opacity="0.35"/>
                  %s
                  <text x="92" y="1114" fill="#f6d59a" font-family="Arial, Helvetica, sans-serif" font-size="30" letter-spacing="3">%s</text>
                </svg>
                """.formatted(shopLines, escapeSvg(title), escapeSvg(subtitle), escapeSvg(offerBadge), captionLines, escapeSvg(contact));
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
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
        String brand = defaultString(trimToNull(shopName), "आमच्या दुकानातून");
        return switch (variant) {
            case 1 -> "\"दररोजच्या लुकला थोडी खास ओळख द्या.\" %s कडून %s मध्ये %s. आवडलेले डिझाइन्स पाहण्यासाठी %s."
                    .formatted(brand, subject, offer, visitLine);
            case 2 -> "तुमच्या स्टाईलची पुढची सुंदर गोष्ट इथून सुरू होते. %s मधील %s. %s."
                    .formatted(subject, offer, visitLine);
            default -> "\"छोटीशी निवड, मोठा कॉन्फिडन्स.\" %s कडून %s साठी %s. %s."
                    .formatted(brand, subject, offer, visitLine);
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
            case 1 -> "\"A small detail can change the whole look.\" %s is here with %s. Explore %s at %s."
                    .formatted(occasion, offer, subject, defaultString(shopName, "our shop"));
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
        String brand = defaultString(shopName, "our shop");
        return switch (variant) {
            case 1 -> "\"The right accessory makes every celebration feel personal.\" For %s, %s brings %s with %s. Visit the shop or website today."
                    .formatted(occasion, brand, subject, offer);
            case 2 -> "Some styles are made for compliments. Discover %s at %s and make %s feel extra special with %s."
                    .formatted(subject, brand, occasion, offer);
            default -> "\"Your best look should be easy to choose.\" %s has fresh %s for %s. %s. Visit us today."
                    .formatted(brand, subject, occasion, offer);
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
        return cleaned;
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

    private String polishGeneratedCta(String cta, MarketingOccasionLibrary.Occasion festivalContext) {
        if (festivalContext == null) {
            return cta;
        }
        return cta.replace(festivalContext.marathiName() + " निमित्त " + festivalContext.marathiName(),
                festivalContext.marathiName() + " निमित्त खास ऑफर");
    }

    private String buildCreativeOfferBadge(Campaign campaign) {
        MarketingOccasionLibrary.Occasion festivalContext = detectFestivalContext(campaign);
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
