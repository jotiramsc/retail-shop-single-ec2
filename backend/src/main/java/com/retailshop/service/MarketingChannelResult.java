package com.retailshop.service;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketingChannelResult {
    private boolean success;
    private String responseId;
    private String errorMessage;
}
