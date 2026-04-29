package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiteInteractionLabelCountResponse {
    private String label;
    private long visits;
}
