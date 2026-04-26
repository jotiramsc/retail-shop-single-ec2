package com.retailshop.dto;

import com.retailshop.enums.CampaignType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampaignRequest {

    @NotBlank
    private String name;

    @NotNull
    private CampaignType type;

    @NotBlank
    private String content;
}
