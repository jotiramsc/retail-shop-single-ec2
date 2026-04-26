package com.retailshop.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
}
