package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CustomerAuthResponse {
    private UUID customerId;
    private String name;
    private String mobile;
    private String token;
}
