package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.MarketingProperties;
import com.retailshop.dto.ImageUploadResponse;
import com.retailshop.entity.Campaign;
import com.retailshop.enums.MarketingCampaignType;
import com.retailshop.enums.MarketingDiscountType;
import com.retailshop.enums.MarketingLanguage;
import com.retailshop.enums.MarketingPlatform;
import com.retailshop.enums.MarketingTone;
import com.retailshop.service.ImageUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIContentGenerationServiceImplTest {

    private final ImageUploadService noOpImageUploadService = new ImageUploadService() {
        @Override
        public ImageUploadResponse uploadImage(MultipartFile image, String category) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public ImageUploadResponse uploadImageBytes(byte[] imageBytes, String contentType, String category) {
            throw new UnsupportedOperationException("Not used in this test");
        }
    };

    @Test
    void shouldGenerateFestivalAwareMarathiDraftsWhenAiKeyMissing() {
        MarketingProperties properties = new MarketingProperties();
        properties.getAi().setEnabled(false);
        AIContentGenerationServiceImpl service = new AIContentGenerationServiceImpl(properties, new ObjectMapper(), noOpImageUploadService);

        Campaign campaign = new Campaign();
        campaign.setCampaignName("Akshaya Tritiya Jewellery Glow");
        campaign.setCampaignType(MarketingCampaignType.FESTIVAL);
        campaign.setOfferTitle("Akshaya Tritiya Jewellery Offer");
        campaign.setDiscountType(MarketingDiscountType.PERCENTAGE);
        campaign.setDiscountValue(BigDecimal.valueOf(20));
        campaign.setLanguage(MarketingLanguage.MARATHI);
        campaign.setTone(MarketingTone.FESTIVE);

        var draft = service.generateDraft(campaign, "Krishnai Pearl Shopee", "JEWELLERY", "नेकलेस आणि बांगड्यांचे खास कलेक्शन", MarketingPlatform.WHATSAPP);

        assertTrue(draft.captionText().contains("अक्षय तृतीया"));
        assertTrue(draft.captionText().contains("20%"));
        assertEquals("आता खरेदी करा", draft.callToAction());
        assertTrue(draft.imagePrompt().contains("Akshaya Tritiya"));
    }

    @Test
    void shouldUseMarathiHashtagsForFestivalInstagramDrafts() {
        MarketingProperties properties = new MarketingProperties();
        properties.getAi().setEnabled(false);
        AIContentGenerationServiceImpl service = new AIContentGenerationServiceImpl(properties, new ObjectMapper(), noOpImageUploadService);

        Campaign campaign = new Campaign();
        campaign.setCampaignName("Gudi Padwa Premium Picks");
        campaign.setCampaignType(MarketingCampaignType.FESTIVAL);
        campaign.setOfferTitle("Gudi Padwa Offer");
        campaign.setDiscountType(MarketingDiscountType.NONE);
        campaign.setLanguage(MarketingLanguage.MARATHI);
        campaign.setTone(MarketingTone.FESTIVE);

        var draft = service.generateDraft(campaign, "Krishnai Pearl Shopee", "COSMETICS", "Premium Saree Pairings", MarketingPlatform.INSTAGRAM);

        assertTrue(draft.captionText().contains("गुढी पाडवा"));
        assertTrue(draft.hashtags().contains("#गुढीपाडवा"));
        assertEquals("आता भेट द्या", draft.callToAction());
    }

    @Test
    void shouldAvoidRepeatingFestivalNameInMarathiInstagramCaption() {
        MarketingProperties properties = new MarketingProperties();
        properties.getAi().setEnabled(false);
        AIContentGenerationServiceImpl service = new AIContentGenerationServiceImpl(properties, new ObjectMapper(), noOpImageUploadService);

        Campaign campaign = new Campaign();
        campaign.setCampaignName("मकर संक्रांत");
        campaign.setCampaignType(MarketingCampaignType.FESTIVAL);
        campaign.setOfferTitle("मकर संक्रांत Offer");
        campaign.setDiscountType(MarketingDiscountType.PERCENTAGE);
        campaign.setDiscountValue(BigDecimal.valueOf(13));
        campaign.setLanguage(MarketingLanguage.MARATHI);
        campaign.setTone(MarketingTone.FESTIVE);

        var draft = service.generateDraft(campaign, "Krishnai Pearl Shopee", "JEWELLERY", null, MarketingPlatform.INSTAGRAM);

        assertTrue(draft.captionText().contains("मकर संक्रांत"));
        assertTrue(draft.captionText().contains("13%"));
        assertFalse(draft.captionText().contains("मकर संक्रांत निमित्त मकर संक्रांत"));
        assertFalse(draft.captionText().contains("Offer वर"));
    }

    @Test
    void shouldGenerateCleanMarathiMothersDayCopy() {
        MarketingProperties properties = new MarketingProperties();
        properties.getAi().setEnabled(false);
        AIContentGenerationServiceImpl service = new AIContentGenerationServiceImpl(properties, new ObjectMapper(), noOpImageUploadService);

        Campaign campaign = new Campaign();
        campaign.setCampaignName("मातृ दिन");
        campaign.setCampaignType(MarketingCampaignType.SEASONAL);
        campaign.setOfferTitle("मातृ दिनासाठी आईंसाठी खास भेटवस्तू");
        campaign.setDiscountType(MarketingDiscountType.PERCENTAGE);
        campaign.setDiscountValue(BigDecimal.valueOf(15));
        campaign.setLanguage(MarketingLanguage.MARATHI);
        campaign.setTone(MarketingTone.EMOTIONAL);

        var draft = service.generateDraft(campaign, "Krishnai Pearl Shopee", "JEWELLERY", null, MarketingPlatform.FACEBOOK);

        assertTrue(draft.captionText().contains("आईने नेहमी आपली काळजी घेतली"));
        assertTrue(draft.captionText().contains("आईसाठी खास भेटवस्तूंवर 15% पर्यंत सूट"));
        assertTrue(draft.hashtags().contains("#मातृदिन"));
        assertEquals("आईसाठी कलेक्शन पाहा", draft.callToAction());
        assertTrue(draft.imagePrompt().contains("Mother's Day"));
        assertTrue(draft.imagePrompt().toLowerCase().contains("mother"));
        assertTrue(draft.imagePrompt().contains("Visual refresh seed"));
        assertTrue(draft.imagePrompt().contains("Make this regeneration visually fresh"));
        assertTrue(draft.imagePrompt().contains("Avoid repeating the same necklace-on-silk flat lay"));
        assertFalse(draft.captionText().contains("मातु"));
        assertFalse(draft.captionText().contains("जपरी"));
        assertFalse(draft.captionText().contains("आईंसाठी"));
    }

    @Test
    void shouldGenerateCleanMarathiWeddingSeasonCopy() {
        MarketingProperties properties = new MarketingProperties();
        properties.getAi().setEnabled(false);
        AIContentGenerationServiceImpl service = new AIContentGenerationServiceImpl(properties, new ObjectMapper(), noOpImageUploadService);

        Campaign campaign = new Campaign();
        campaign.setCampaignName("लग्नाचा सीझन");
        campaign.setCampaignType(MarketingCampaignType.SEASONAL);
        campaign.setOfferTitle("लग्नाच्या सीझनसाठी ब्रायडल आणि गिफ्ट कलेक्शन");
        campaign.setDiscountType(MarketingDiscountType.PERCENTAGE);
        campaign.setDiscountValue(BigDecimal.valueOf(30));
        campaign.setLanguage(MarketingLanguage.MARATHI);
        campaign.setTone(MarketingTone.LUXURY);

        var draft = service.generateDraft(campaign, "Krishnai Pearl Shopee", "JEWELLERY", null, MarketingPlatform.FACEBOOK);

        assertTrue(draft.captionText().contains("लग्नसराईसाठी ब्रायडल आणि गिफ्ट कलेक्शनवर 30% पर्यंत सूट"));
        assertEquals("लग्नसराई कलेक्शन पाहा", draft.callToAction());
        assertTrue(draft.imagePrompt().toLowerCase().contains("wedding"));
        assertTrue(draft.imagePrompt().toLowerCase().contains("jewellery"));
        assertTrue(draft.imagePrompt().contains("Visual refresh seed"));
        assertTrue(draft.imagePrompt().contains("Make this regeneration visually fresh"));
        assertTrue(draft.imagePrompt().contains("Avoid repeating the same necklace-on-silk flat lay"));
        assertFalse(draft.captionText().contains("लग्नाचा सीझन साठी"));
        assertFalse(draft.captionText().contains("लग्नाच्या सीझनसाठी"));
        assertFalse(draft.captionText().contains("लकशरी"));
    }

    @Test
    void shouldValidateMarathiImageOverlayHeadlineBeforeRendering() throws Exception {
        MarketingProperties properties = new MarketingProperties();
        properties.getAi().setEnabled(false);
        AIContentGenerationServiceImpl service = new AIContentGenerationServiceImpl(properties, new ObjectMapper(), noOpImageUploadService);

        Campaign campaign = new Campaign();
        campaign.setCampaignName("दिवाळी");
        campaign.setCampaignType(MarketingCampaignType.FESTIVAL);
        campaign.setCampaignGoal("GREETING");
        campaign.setDiscountType(MarketingDiscountType.NONE);
        campaign.setLanguage(MarketingLanguage.MARATHI);
        campaign.setTone(MarketingTone.FESTIVE);

        String headline = invokeCreativeImageHeadline(service, campaign, null);

        assertEquals("दिवाळीच्या हार्दिक शुभेच्छा", headline);
        assertFalse(headline.contains("दिवाळी साठी"));
        assertFalse(headline.contains("दिवाळी साठी दिवाळीच्या शुभेच्छा"));
    }

    @Test
    void shouldAddGreetingQuoteForFestivalCreativeImages() throws Exception {
        MarketingProperties properties = new MarketingProperties();
        properties.getAi().setEnabled(false);
        AIContentGenerationServiceImpl service = new AIContentGenerationServiceImpl(properties, new ObjectMapper(), noOpImageUploadService);

        Campaign campaign = new Campaign();
        campaign.setCampaignName("Women's Day greeting");
        campaign.setCampaignType(MarketingCampaignType.FESTIVAL);
        campaign.setCampaignGoal("GREETING");
        campaign.setDiscountType(MarketingDiscountType.NONE);
        campaign.setLanguage(MarketingLanguage.ENGLISH);
        campaign.setTone(MarketingTone.FESTIVE);

        String quote = invokeCreativeImageQuote(service, campaign);
        String headline = invokeCreativeImageHeadline(service, campaign, null);

        assertEquals("Happy Women's Day", quote);
        assertEquals("Happy Women's Day", headline);
    }

    @Test
    void shouldAddCleanMarathiGreetingQuoteForRepublicDayCreativeImages() throws Exception {
        MarketingProperties properties = new MarketingProperties();
        properties.getAi().setEnabled(false);
        AIContentGenerationServiceImpl service = new AIContentGenerationServiceImpl(properties, new ObjectMapper(), noOpImageUploadService);

        Campaign campaign = new Campaign();
        campaign.setCampaignName("प्रजासत्ताक दिन");
        campaign.setCampaignType(MarketingCampaignType.FESTIVAL);
        campaign.setCampaignGoal("GREETING");
        campaign.setDiscountType(MarketingDiscountType.NONE);
        campaign.setLanguage(MarketingLanguage.MARATHI);
        campaign.setTone(MarketingTone.FESTIVE);

        String quote = invokeCreativeImageQuote(service, campaign);

        assertEquals("प्रजासत्ताक दिनाच्या हार्दिक शुभेच्छा", quote);
        assertFalse(quote.contains("साठी"));
    }

    @Test
    void shouldChangeSharedImagePromptForEachRegenerationSeed() {
        MarketingProperties properties = new MarketingProperties();
        properties.getAi().setEnabled(false);
        AIContentGenerationServiceImpl service = new AIContentGenerationServiceImpl(properties, new ObjectMapper(), noOpImageUploadService);

        Campaign campaign = new Campaign();
        campaign.setCampaignName("मातृ दिन");
        campaign.setCampaignType(MarketingCampaignType.SEASONAL);
        campaign.setOfferTitle("मातृ दिनासाठी आईंसाठी खास भेटवस्तू");
        campaign.setDiscountType(MarketingDiscountType.PERCENTAGE);
        campaign.setDiscountValue(BigDecimal.valueOf(15));
        campaign.setLanguage(MarketingLanguage.MARATHI);
        campaign.setTone(MarketingTone.EMOTIONAL);

        var first = service.generateSharedCreativeImage(campaign, "Krishnai Pearl Shopee", "JEWELLERY", null, "first-regeneration");
        var second = service.generateSharedCreativeImage(campaign, "Krishnai Pearl Shopee", "JEWELLERY", null, "second-regeneration");

        assertNotEquals(first.imagePrompt(), second.imagePrompt());
        assertTrue(first.imagePrompt().contains("Visual refresh seed: firstregen"));
        assertTrue(second.imagePrompt().contains("Visual refresh seed: secondrege"));
        assertTrue(first.imagePrompt().contains("Make this regeneration visually fresh"));
        assertTrue(second.imagePrompt().contains("Make this regeneration visually fresh"));
    }

    private String invokeCreativeImageHeadline(AIContentGenerationServiceImpl service,
                                               Campaign campaign,
                                               String productName) throws Exception {
        Method detectFestivalContext = AIContentGenerationServiceImpl.class.getDeclaredMethod("detectFestivalContext", Campaign.class);
        detectFestivalContext.setAccessible(true);
        Object festivalContext = detectFestivalContext.invoke(service, campaign);

        Method headline = AIContentGenerationServiceImpl.class.getDeclaredMethod(
                "buildCreativeImageHeadline",
                Campaign.class,
                String.class,
                Class.forName("com.retailshop.service.impl.MarketingOccasionLibrary$Occasion")
        );
        headline.setAccessible(true);
        return (String) headline.invoke(service, campaign, productName, festivalContext);
    }

    private String invokeCreativeImageQuote(AIContentGenerationServiceImpl service,
                                            Campaign campaign) throws Exception {
        Method detectFestivalContext = AIContentGenerationServiceImpl.class.getDeclaredMethod("detectFestivalContext", Campaign.class);
        detectFestivalContext.setAccessible(true);
        Object festivalContext = detectFestivalContext.invoke(service, campaign);

        Method quote = AIContentGenerationServiceImpl.class.getDeclaredMethod(
                "buildCreativeImageQuote",
                Campaign.class,
                Class.forName("com.retailshop.service.impl.MarketingOccasionLibrary$Occasion")
        );
        quote.setAccessible(true);
        return (String) quote.invoke(service, campaign, festivalContext);
    }

    @Test
    void shouldFallbackToInlineCreativePreviewWhenAiImageGenerationIsUnavailable() {
        MarketingProperties properties = new MarketingProperties();
        properties.getAi().setEnabled(false);
        AIContentGenerationServiceImpl service = new AIContentGenerationServiceImpl(properties, new ObjectMapper(), noOpImageUploadService);

        Campaign campaign = new Campaign();
        campaign.setCampaignName("Summer Glow");
        campaign.setCampaignType(MarketingCampaignType.SEASONAL);
        campaign.setLanguage(MarketingLanguage.ENGLISH);
        campaign.setTone(MarketingTone.PREMIUM);

        var draft = service.generateDraft(campaign, "Retail App", "COSMETICS", "Glow Serum", MarketingPlatform.FACEBOOK);

        assertTrue(draft.imageUrl().startsWith("data:image/svg+xml;base64,"));
    }
}
