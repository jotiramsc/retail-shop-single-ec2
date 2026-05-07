package com.retailshop;

import com.retailshop.config.AppProperties;
import com.retailshop.config.MarketingProperties;
import com.retailshop.config.OmnichannelProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, MarketingProperties.class, OmnichannelProperties.class})
public class RetailShopApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(RetailShopApplication.class, args);
    }
}
