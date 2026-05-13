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

    @Getter
    @Setter
    public static class Ai {
        private boolean enabled = true;
        private String apiKey = "";
        private String model = "gpt-4.1-mini";
        private String imageModel = "gpt-image-1.5";
        private String imageSize = "1024x1024";
        private String imageQuality = "medium";
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
        private String phoneNumberId = "";
    }
}
