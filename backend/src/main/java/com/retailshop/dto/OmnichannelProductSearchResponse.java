package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class OmnichannelProductSearchResponse {
    private String introMessage;
    private String query;
    private int totalMatches;
    private List<OmnichannelProductCardResponse> products;
}
