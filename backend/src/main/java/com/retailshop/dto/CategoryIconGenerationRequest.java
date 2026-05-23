package com.retailshop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryIconGenerationRequest {
    @NotBlank
    private String categoryName;

    private String primaryColor;
    private String accentColor;
    private String detailColor;
}
