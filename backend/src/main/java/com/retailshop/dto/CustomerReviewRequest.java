package com.retailshop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerReviewRequest {
    private String customerName;
    @NotNull
    private String mobile;
    private String city;
    private String product;
    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;
    private String comment;
}
