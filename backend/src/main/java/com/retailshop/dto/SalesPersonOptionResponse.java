package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class SalesPersonOptionResponse {
    private UUID id;
    private String username;
    private String displayName;
}
