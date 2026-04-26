package com.retailshop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductCategoryOptionRequest {
    @NotBlank
    private String displayName;

    private Boolean active;
}
