package com.retailshop.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "omnichannel")
public class OmnichannelProperties {

    private String websiteBaseUrl = "https://kpskrishnai.com";
    private String webhookVerifyToken = "";
    private String webhookSecret = "";
    private String defaultCouponCode = "";
    private int maxProductCards = 6;
}
