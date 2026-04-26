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
    private CustomerAuth customerAuth = new CustomerAuth();
    private Msg91 msg91 = new Msg91();
    private Pricing pricing = new Pricing();
    private Redis redis = new Redis();
    private Razorpay razorpay = new Razorpay();
    private PhonePe phonepe = new PhonePe();
    private Twilio twilio = new Twilio();
    private Meta meta = new Meta();

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
    public static class Razorpay {
        private String keyId = "";
        private String keySecret = "";
        private String webhookSecret = "";
    }

    @Getter
    @Setter
    public static class PhonePe {
        private String clientId = "";
        private String clientSecret = "";
        private int clientVersion = 1;
        private String env = "sandbox";
        private String redirectUrl = "";
        private String webhookUrl = "";
    }

    @Getter
    @Setter
    public static class Twilio {
        private String accountSid = "";
        private String authToken = "";
        private String verifyServiceSid = "";
        private String verifyFriendlyName = "Retail Shop OTP";
        private String channel = "whatsapp";
        private String whatsappFrom = "";
        private String otpContentSid = "";
        private String orderUpdateContentSid = "";
        private String offerContentSid = "";
    }

    @Getter
    @Setter
    public static class Meta {
        private String accessToken = "";
        private String pageId = "";
        private String instagramBusinessAccountId = "";
        private String graphVersion = "v23.0";
    }
}
