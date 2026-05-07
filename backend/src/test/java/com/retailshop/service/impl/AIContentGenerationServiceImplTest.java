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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals("आता कलेक्शन पाहा", draft.callToAction());
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
