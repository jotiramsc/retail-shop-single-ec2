package com.retailshop.dto;

import com.retailshop.enums.CampaignType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CampaignRequest {

    private String name;
    private CampaignType type;
    private String title;
    private String offerProduct;

    @NotBlank
    private String content;

    private String mediaUrl;
    private String hashtags;
    private String linkUrl;

    @Size(min = 1)
    private List<CampaignType> channels;

    private boolean publishNow = true;
}
