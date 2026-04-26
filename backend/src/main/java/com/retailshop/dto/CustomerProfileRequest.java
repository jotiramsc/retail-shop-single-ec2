package com.retailshop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerProfileRequest {

    @NotBlank
    private String name;
}
