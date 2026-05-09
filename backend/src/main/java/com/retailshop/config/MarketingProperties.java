package com.retailshop.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "marketing")
public class MarketingProperties {

    private Ai ai = new Ai();
    private Meta meta = new Meta();
    private WhatsApp whatsapp = new WhatsApp();
    private Twilio twilio = new Twilio();
    private Gupshup gupshup = new Gupshup();
    private Leonardo leonardo = new Leonardo();

    @Getter
    @Setter
    public static class Ai {
        private boolean enabled = true;
        private String apiKey = "";
        private String model = "gpt-4.1-mini";
        private String imageProvider = "OPENAI";
        private String imageModel = "gpt-image-1.5";
        private String imageSize = "1024x1024";
        private String imageQuality = "medium";
    }

    @Getter
    @Setter
    public static class Leonardo {
        private String apiKey = "";
        private String modelId = "de7d3faf-762f-48e0-b3b7-9d0ac3a3fcf3";
        private String styleUuid = "111dc692-d470-4eec-b791-3475abac4c46";
        private boolean alchemy = true;
        private boolean enhancePrompt = false;
        private boolean publicImages = true;
        private int width = 1024;
        private int height = 1024;
        private double contrast = 3.5;
        private int pollAttempts = 16;
        private int pollDelayMs = 1500;
    }

    @Getter
    @Setter
    public static class Meta {
        private String accessToken = "";
        private String instagramBusinessAccountId = "";
        private String facebookPageId = "";
        private String graphVersion = "v23.0";
    }

    @Getter
    @Setter
    public static class WhatsApp {
        private String provider = "GUPSHUP";
        private String phoneNumberId = "";
    }

    @Getter
    @Setter
    public static class Twilio {
        private String accountSid = "";
        private String authToken = "";
        private String whatsappFrom = "";
        private String offerContentSid = "";
    }

    @Getter
    @Setter
    public static class Gupshup {
        private String apiKey = "";
        private String appName = "";
        private String sourceNumber = "";
        private String templateId = "";
    }
}
