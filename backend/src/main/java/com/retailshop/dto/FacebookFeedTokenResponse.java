package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FacebookFeedTokenResponse {
    private String token;
}
