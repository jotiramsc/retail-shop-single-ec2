package com.retailshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductCategoryOptionRequest {
    @NotBlank
    private String displayName;

    @Size(max = 4000)
    private String iconImageUrl;

    private Boolean active;
}
