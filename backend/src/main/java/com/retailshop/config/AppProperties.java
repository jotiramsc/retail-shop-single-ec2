package com.retailshop.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Min(0)
    private int lowStockThreshold = 5;

    @Min(1)
    private int slowMovingMinStock = 10;

    @Min(0)
    private int slowMovingMaxUnitsSold = 3;

    private Aws aws = new Aws();
    private StaffAuth staffAuth = new StaffAuth();
    private CustomerAuth customerAuth = new CustomerAuth();
    private Msg91 msg91 = new Msg91();
    private Pricing pricing = new Pricing();
    private Redis redis = new Redis();
    private Payment payment = new Payment();
    private Razorpay razorpay = new Razorpay();
    private String whatsappProvider = "META";
    private Gupshup gupshup = new Gupshup();
    private Meta meta = new Meta();
    private GoogleMaps googleMaps = new GoogleMaps();
    private GoogleAuth googleAuth = new GoogleAuth();

    @Getter
    @Setter
    public static class Aws {
        private String region = "us-east-1";
        private String s3Bucket = "";
        private String cloudfrontDomain = "";
        private String accessKeyId = "";
        private String secretAccessKey = "";
        private boolean migrateImagesOnStart = false;

        @Min(1)
        private long uploadMaxBytes = 5_242_880L;
    }

    @Getter
    @Setter
    public static class StaffAuth {
        private String jwtSecret = "local-dev-staff-secret-change-me";
        private long jwtTtlMinutes = 480L;
    }

    @Getter
    @Setter
    public static class CustomerAuth {
        private String jwtSecret = "local-dev-customer-secret-change-me";
        private long jwtTtlMinutes = 43_200L;
        private long otpTtlMinutes = 5L;
        private long otpRateLimitSeconds = 30L;
        private int otpLength = 6;
        private int otpMaxAttempts = 5;
    }

    @Getter
    @Setter
    public static class Msg91 {
        private String authKey = "";
        private String templateId = "";

        @Min(4)
        private int otpLength = 6;
    }

    @Getter
    @Setter
    public static class Pricing {
        @Min(0)
        private BigDecimal taxPercent = BigDecimal.ZERO;

        @Min(0)
        private BigDecimal deliveryCharge = BigDecimal.ZERO;

        @Min(0)
        private BigDecimal freeDeliveryMinOrder = BigDecimal.ZERO;
    }

    @Getter
    @Setter
    public static class Redis {
        private boolean enabled = false;
        private String otpKeyPrefix = "retail:otp:";
        private String cartKeyPrefix = "retail:cart:";
        private long cartTtlMinutes = 30L;
    }

    @Getter
    @Setter
    public static class Payment {
        private String provider = "RAZORPAY";
    }

    @Getter
    @Setter
    public static class Razorpay {
        private String keyId = "rzp_test_SirLkQHBMbkN2d";
        private String keySecret = "";
        private String webhookSecret = "";
    }

    @Getter
    @Setter
    public static class Gupshup {
        private String apiKey = "";
        private String appName = "";
        private String sourceNumber = "";
        private String messageEndpoint = "https://api.gupshup.io/wa/api/v1/msg";
        private String templateEndpoint = "https://api.gupshup.io/wa/api/v1/template/msg";
        private int maxAttempts = 3;
        private long retryDelayMs = 750L;
        private GupshupTemplates templates = new GupshupTemplates();
    }

    @Getter
    @Setter
    public static class GupshupTemplates {
        private String otp = "";
        private String orderConfirmation = "";
        private String orderDispatched = "";
        private String orderDelivered = "";
        private String orderCancelled = "";
        private String orderReturned = "";
        private String refundInitiated = "";
        private String paymentFailed = "";
        private String paymentSuccess = "";
        private String botWelcome = "";
        private String botMenu = "";
        private String supportEscalation = "";
        private String outOfOffice = "";
        private String feedbackRequest = "";
    }

    @Getter
    @Setter
    public static class Meta {
        private String accessToken = "";
        private String pageId = "";
        private String pageAccessToken = "";
        private String instagramBusinessAccountId = "";
        private String graphVersion = "v23.0";
        private String whatsappPhoneNumberId = "";
        private String whatsappOtpTemplateName = "";
        private String whatsappOtpTemplateLanguage = "en_US";
        private int whatsappMaxAttempts = 3;
        private long whatsappRetryDelayMs = 750L;
        private WhatsappTemplates whatsappTemplates = new WhatsappTemplates();
        private String appId = "";
        private String appSecret = "";
        private String exchangeToken = "";
        private String tokenRefreshSecretId = "";
        private boolean tokenRefreshEnabled = true;
        private long tokenRefreshIntervalMs = 3_888_000_000L;
        private long tokenRefreshInitialDelayMs = 60_000L;
    }

    @Getter
    @Setter
    public static class WhatsappTemplates {
        private String defaultLanguage = "en_US";
        private Template orderConfirmation = new Template("kp_order_confirmed_en", "en_US");
        private Template orderDispatched = new Template("kp_order_dispatched_en", "en_US");
        private Template orderDelivered = new Template("kp_order_delivered_en", "en_US");
        private Template orderCancelled = new Template("kp_order_cancelled_en", "en_US");
        private Template orderReturned = new Template("kp_order_returned_en", "en_US");
        private Template refundInitiated = new Template("kp_refund_initiated_en", "en_US");
        private Template paymentFailed = new Template("kp_payment_failed_en", "en_US");
        private Template paymentSuccess = new Template("kp_payment_success_en", "en_US");
        private Template botWelcome = new Template("kp_bot_welcome_en", "en_US");
        private Template botMenu = new Template("kp_bot_menu_en", "en_US");
        private Template supportEscalation = new Template("kp_support_escalation_en", "en_US");
        private Template outOfOffice = new Template("kp_out_of_office_en", "en_US");
        private Template feedbackRequest = new Template("kp_feedback_request_en", "en_US");
    }

    @Getter
    @Setter
    public static class Template {
        private String name;
        private String language;

        public Template() {
        }

        public Template(String name, String language) {
            this.name = name;
            this.language = language;
        }
    }

    @Getter
    @Setter
    public static class GoogleMaps {
        private String apiKey = "";
    }

    @Getter
    @Setter
    public static class GoogleAuth {
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";
    }
}
