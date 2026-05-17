package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryIconOptionResponse {
    private String label;
    private String imageUrl;
}
