package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class LoginResponse {
    private String username;
    private String role;
    private String displayName;
    private List<String> permissions;
}
