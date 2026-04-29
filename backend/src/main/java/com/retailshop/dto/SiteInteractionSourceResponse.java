package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiteInteractionSourceResponse {
    private String sourceType;
    private String sourceLabel;
    private long visits;
}
