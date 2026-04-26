package com.retailshop;

import com.retailshop.config.AppProperties;
import com.retailshop.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class, SecurityProperties.class})
public class RetailShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetailShopApplication.class, args);
    }
}
